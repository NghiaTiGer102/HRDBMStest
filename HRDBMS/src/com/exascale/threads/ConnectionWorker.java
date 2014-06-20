package com.exascale.threads;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicLong;
import com.exascale.compression.CompressedSocket;
import com.exascale.filesystem.Block;
import com.exascale.filesystem.Page;
import com.exascale.filesystem.RID;
import com.exascale.logging.LogRec;
import com.exascale.managers.HRDBMSWorker;
import com.exascale.managers.LogManager;
import com.exascale.managers.XAManager;
import com.exascale.misc.AtomicDouble;
import com.exascale.misc.BufferedLinkedBlockingQueue;
import com.exascale.misc.DataEndMarker;
import com.exascale.misc.MultiHashMap;
import com.exascale.misc.MyDate;
import com.exascale.optimizer.Index;
import com.exascale.optimizer.MetaData;
import com.exascale.optimizer.MetaData.PartitionMetaData;
import com.exascale.optimizer.NetworkSendOperator;
import com.exascale.optimizer.RIDAndIndexKeys;
import com.exascale.optimizer.UpdateSetAndRAIK;
import com.exascale.tables.DataType;
import com.exascale.tables.RIDChange;
import com.exascale.tables.Schema;
import com.exascale.tables.Transaction;
import com.exascale.tables.Schema.FieldValue;
import com.exascale.tables.Schema.Row;
import com.exascale.tables.Schema.RowIterator;

public class ConnectionWorker extends HRDBMSThread
{
	private final CompressedSocket sock;
	private static HashMap<Integer, NetworkSendOperator> sends;
	private boolean clientConnection = false;
	private Transaction tx = null;
	private XAWorker worker;

	static
	{
		sends = new HashMap<Integer, NetworkSendOperator>();
	}

	public ConnectionWorker(CompressedSocket sock)
	{
		this.description = "Connection Worker";
		this.sock = sock;
	}

	private static byte[] intToBytes(int val)
	{
		final byte[] buff = new byte[4];
		buff[0] = (byte)(val >> 24);
		buff[1] = (byte)((val & 0x00FF0000) >> 16);
		buff[2] = (byte)((val & 0x0000FF00) >> 8);
		buff[3] = (byte)((val & 0x000000FF));
		return buff;
	}

	@Override
	public void run()
	{
		HRDBMSWorker.logger.debug("New connection worker is up and running");
		try
		{
			while (true)
			{
				final byte[] cmd = new byte[8];
				final InputStream in = sock.getInputStream();
				final OutputStream out = sock.getOutputStream();
				int num = in.read(cmd);
				if (num != 8 && num != -1)
				{
					HRDBMSWorker.logger.error("Connection worker received less than 8 bytes when reading a command.  Terminating!");
					returnException("BadCommandException");
					sock.close();
					if (worker != null)
					{
						ArrayList<Object> cmd2 = new ArrayList<Object>(1);
						cmd2.add("CLOSE");
						worker.in.put(cmd2);
					}
					this.terminate();
					return;
				}
				else if (num == -1)
				{
					HRDBMSWorker.logger.debug("Received EOF when looking for a command.");
					sock.close();
					if (worker != null)
					{
						ArrayList<Object> cmd2 = new ArrayList<Object>(1);
						cmd2.add("CLOSE");
						worker.in.put(cmd2);
					}
					this.terminate();
					return;
				}
				final String command = new String(cmd, "UTF-8");
				HRDBMSWorker.logger.debug("Received " + num + " bytes");
				HRDBMSWorker.logger.debug("Command received by connection worker: " + command);

				final byte[] fromBytes = new byte[4];
				final byte[] toBytes = new byte[4];
				num = in.read(fromBytes);
				if (num != 4)
				{
					HRDBMSWorker.logger.error("Received less than 4 bytes when reading from field in remote command.");
					HRDBMSWorker.logger.debug("Received " + num + " bytes");
					returnException("InvalidFromIDException");
					sock.close();
					if (worker != null)
					{
						ArrayList<Object> cmd2 = new ArrayList<Object>(1);
						cmd2.add("CLOSE");
						worker.in.put(cmd2);
					}
					this.terminate();
					return;
				}
				HRDBMSWorker.logger.debug("Read " + num + " bytes");
				HRDBMSWorker.logger.debug("From field received by connection worker.");
				num = in.read(toBytes);
				if (num != 4)
				{
					HRDBMSWorker.logger.error("Received less than 4 bytes when reading to field in remote command.");
					returnException("InvalidToIDException");
					sock.close();
					if (worker != null)
					{
						ArrayList<Object> cmd2 = new ArrayList<Object>(1);
						cmd2.add("CLOSE");
						worker.in.put(cmd2);
					}
					this.terminate();
					return;
				}
				HRDBMSWorker.logger.debug("Read " + num + " bytes");
				HRDBMSWorker.logger.debug("To field received by connection worker.");
				final int from = bytesToInt(fromBytes);
				final int to = bytesToInt(toBytes);

				if (command.equals("RGETDATD"))
				{
					try
					{
						final String retval = getDataDir(from, to);
						HRDBMSWorker.logger.debug("Responding with " + retval);
						respond(to, from, retval);
					}
					catch (final Exception e)
					{
						returnException(e.toString());
					}
				}
				else if (command.equals("REMOTTRE"))
				{
					final ObjectInputStream objIn = new ObjectInputStream(in);
					final NetworkSendOperator op = (NetworkSendOperator)objIn.readObject();
					op.setSocket(sock);
					op.start();
					try
					{
						op.close();
					}
					catch(Exception e)
					{}
				}
				else if (command.equals("SNDRMTTR"))
				{
					final byte[] idBytes = new byte[4];
					num = in.read(idBytes);
					if (num != 4)
					{
						throw new Exception("Received less than 4 bytes when reading id field in SNDRMTTR command.");
					}

					final int id = bytesToInt(idBytes);
					if (!sends.containsKey(id))
					{
						out.write(1); // do send
						out.flush();
						final ObjectInputStream objIn = new ObjectInputStream(in);
						final NetworkSendOperator op = (NetworkSendOperator)objIn.readObject();
						synchronized (sends)
						{
							if (!sends.containsKey(id))
							{
								sends.put(id, op);
							}
						}
					}
					else
					{
						out.write(0); // don't send
						out.flush();
					}

					final NetworkSendOperator send = sends.get(id);
					//System.out.println("Adding connection from " + from + " to " + send + " = " + sock);
					send.addConnection(from, sock);
					synchronized (send)
					{
						if (send.notStarted() && send.hasAllConnections())
						{
							send.start();
							try
							{
								send.close();
							}
							catch(Exception e)
							{}
							sends.remove(id);
						}
					}
				}
				else if (command.equals("CLOSE   "))
				{
					closeConnection();
					if (worker != null)
					{
						ArrayList<Object> cmd2 = new ArrayList<Object>(1);
						cmd2.add("CLOSE");
						worker.in.put(cmd2);
					}
					this.terminate();
					return;
				}
				else if (command.equals("CLIENT  "))
				{
					clientConnection();
				}
				else if (command.equals("COMMIT  "))
				{
					doCommit();
				}
				else if (command.equals("ROLLBACK"))
				{
					doRollback();
				}
				else if (command.equals("ISOLATIO"))
				{
					doIsolation();
				}
				else if (command.equals("TEST    "))
				{
					testConnection();
				}
				else if (command.equals("SETSCHMA"))
				{
					setSchema();
				}
				else if (command.equals("GETSCHMA"))
				{
					getSchema();
				}
				else if (command.equals("EXECUTEQ"))
				{
					executeQuery();
				}
				else if (command.equals("RSMETA  "))
				{
					getRSMeta();
				}
				else if (command.equals("NEXT    "))
				{
					doNext();
				}
				else if (command.equals("CLOSERS "))
				{
					closeRS();
				}
				else if (command.equals("LROLLBCK"))
				{
					localRollback();
				}
				else if (command.equals("PREPARE "))
				{
					prepare();
				}
				else if (command.equals("LCOMMIT "))
				{
					localCommit();
				}
				else if (command.equals("CHECKTX "))
				{
					checkTx();
				}
				else if (command.equals("MDELETE "))
				{
					massDelete();
				}
				else if (command.equals("DELETE  "))
				{
					delete();
				}
				else if (command.equals("INSERT  "))
				{
					insert();
				}
				else if (command.equals("UPDATE  "))
				{
					update();
				}
				else if (command.equals("EXECUTEU"))
				{
					executeUpdate();
				}
				else
				{
					HRDBMSWorker.logger.error("Uknown command received by ConnectionWorker: " + command);
					returnException("BadCommandException");
				}
			}
		}
		catch (final Exception e)
		{
			try
			{
				returnException(e.toString());
				sock.close();
			}
			catch (final Exception f)
			{
			}
			if (worker != null)
			{
				ArrayList<Object> cmd2 = new ArrayList<Object>(1);
				cmd2.add("CLOSE");
				while(true)
				{
					try
					{
						worker.in.put(cmd2);
						break;
					}
					catch(Exception f)
					{}
				}
			}
			this.terminate();
			return;
		}
	}

	private int bytesToInt(byte[] val)
	{
		final int ret = java.nio.ByteBuffer.wrap(val).getInt();
		return ret;
	}

	private void closeConnection()
	{
		clientConnection = false;
		if (tx != null)
		{
			try
			{
				XAManager.rollback(tx);
			}
			catch(Exception e)
			{
				HRDBMSWorker.logger.error("", e);
			}
		}
		try
		{
			sock.close();
			if (worker != null)
			{
				ArrayList<Object> cmd2 = new ArrayList<Object>(1);
				cmd2.add("CLOSE");
				worker.in.put(cmd2);
			}
			this.terminate();
		}
		catch (final Exception e)
		{
		}
	}

	private String getDataDir(int from, int to)
	{
		return to + "," + HRDBMSWorker.getHParms().getProperty("data_directories");
	}

	private void respond(int from, int to, String retval) throws UnsupportedEncodingException, IOException
	{
		final byte[] type = "RESPOK  ".getBytes("UTF-8");
		final byte[] fromBytes = intToBytes(from);
		final byte[] toBytes = intToBytes(to);
		final byte[] ret = retval.getBytes("UTF-8");
		final byte[] retSize = intToBytes(ret.length);
		final byte[] data = new byte[type.length + fromBytes.length + toBytes.length + retSize.length + ret.length];
		System.arraycopy(type, 0, data, 0, type.length);
		System.arraycopy(fromBytes, 0, data, type.length, fromBytes.length);
		System.arraycopy(toBytes, 0, data, type.length + fromBytes.length, toBytes.length);
		System.arraycopy(retSize, 0, data, type.length + fromBytes.length + toBytes.length, retSize.length);
		System.arraycopy(ret, 0, data, type.length + fromBytes.length + toBytes.length + retSize.length, ret.length);
		HRDBMSWorker.logger.debug("In respond(), response is " + data);
		sock.getOutputStream().write(data);
	}

	private void returnException(String e) throws UnsupportedEncodingException, IOException
	{
		final byte[] type = "EXCEPT  ".getBytes("UTF-8");
		final byte[] ret = e.getBytes("UTF-8");
		final byte[] retSize = intToBytes(ret.length);
		final byte[] data = new byte[type.length + retSize.length + ret.length];
		System.arraycopy(type, 0, data, 0, type.length);
		System.arraycopy(retSize, 0, data, type.length, retSize.length);
		System.arraycopy(ret, 0, data, type.length + retSize.length, ret.length);
		sock.getOutputStream().write(data);
	}
	
	public void clientConnection()
	{
		try
		{
			sock.getOutputStream().write("OK".getBytes("UTF-8"));
			sock.getOutputStream().flush();
		}
		catch(Exception e)
		{
			try
			{
				sock.close();
				if (worker != null)
				{
					ArrayList<Object> cmd2 = new ArrayList<Object>(1);
					cmd2.add("CLOSE");
					worker.in.put(cmd2);
				}
				this.terminate();
			}
			catch(Exception f)
			{}
			return;
		}
		
		clientConnection = true;
	}
	
	public void doCommit()
	{
		if (tx == null)
		{
			sendOK();
		}
		
		try
		{
			XAManager.commit(tx);
		}
		catch(Exception e)
		{
			sendNo();
		}
		
		sendOK();
	}
	
	public void doRollback()
	{
		if (tx == null)
		{
			sendOK();
		}
		
		try
		{
			XAManager.rollback(tx);
		}
		catch(Exception e)
		{
			sendNo();
		}
		
		sendOK();
	}
	
	private void sendOK()
	{
		try
		{
			sock.getOutputStream().write("OK".getBytes("UTF-8"));
			sock.getOutputStream().flush();
		}
		catch(Exception e)
		{
			if (tx != null)
			{
				try
				{
					XAManager.rollback(tx);
				}
				catch(Exception f)
				{
					HRDBMSWorker.logger.error("", f);
				}
			}
			
			clientConnection = false;
			try
			{
				sock.close();
				if (worker != null)
				{
					ArrayList<Object> cmd2 = new ArrayList<Object>(1);
					cmd2.add("CLOSE");
					worker.in.put(cmd2);
				}
				this.terminate();
			}
			catch(Exception f)
			{
				if (worker != null)
				{
					ArrayList<Object> cmd2 = new ArrayList<Object>(1);
					cmd2.add("CLOSE");
					while (true)
					{
						try
						{
							worker.in.put(cmd2);
							break;
						}
						catch(Exception g)
						{}
					}
				}
				this.terminate();
			}
		}
	}
	
	private void sendInt(int val)
	{
		try
		{
			byte[] data = intToBytes(val);
			sock.getOutputStream().write(data);
			sock.getOutputStream().flush();
		}
		catch(Exception e)
		{
			if (tx != null)
			{
				try
				{
					XAManager.rollback(tx);
				}
				catch(Exception f)
				{
					HRDBMSWorker.logger.error("", f);
				}
			}
			
			clientConnection = false;
			try
			{
				sock.close();
				if (worker != null)
				{
					ArrayList<Object> cmd2 = new ArrayList<Object>(1);
					cmd2.add("CLOSE");
					worker.in.put(cmd2);
				}
				this.terminate();
			}
			catch(Exception f)
			{
				if (worker != null)
				{
					ArrayList<Object> cmd2 = new ArrayList<Object>(1);
					cmd2.add("CLOSE");
					while (true)
					{
						try
						{
							worker.in.put(cmd2);
							break;
						}
						catch(Exception g)
						{}
					}
				}
				this.terminate();
			}
		}
	}
	
	private void sendNo()
	{
		try
		{
			sock.getOutputStream().write("NO".getBytes("UTF-8"));
			sock.getOutputStream().flush();
		}
		catch(Exception e)
		{
			if (tx != null)
			{
				try
				{
					XAManager.rollback(tx);
				}
				catch(Exception f)
				{
					HRDBMSWorker.logger.error("", f);
				}
			}
			
			clientConnection = false;
			try
			{
				sock.close();
				if (worker != null)
				{
					ArrayList<Object> cmd2 = new ArrayList<Object>(1);
					cmd2.add("CLOSE");
					worker.in.put(cmd2);
				}
				this.terminate();
			}
			catch(Exception f)
			{
				if (worker != null)
				{
					ArrayList<Object> cmd2 = new ArrayList<Object>(1);
					cmd2.add("CLOSE");
					while (true)
					{
						try
						{
							worker.in.put(cmd2);
							break;
						}
						catch(Exception g)
						{}
					}
				}
				this.terminate();
			}
		}
	}
	
	private void doIsolation()
	{
		byte[] arg = new byte[4];
		int count = 0;
		while (count < 4)
		{
			try
			{
				int temp = sock.getInputStream().read(arg, count, 4 - count);
				if (temp == -1)
				{
					if (tx != null)
					{
						XAManager.rollback(tx);
					}
					
					clientConnection = false;
					try
					{
						sock.close();
						if (worker != null)
						{
							ArrayList<Object> cmd2 = new ArrayList<Object>(1);
							cmd2.add("CLOSE");
							worker.in.put(cmd2);
						}
						this.terminate();
					}
					catch(Exception f)
					{
						if (worker != null)
						{
							ArrayList<Object> cmd2 = new ArrayList<Object>(1);
							cmd2.add("CLOSE");
							worker.in.put(cmd2);
						}
						this.terminate();
					}
				}
				else
				{
					count += temp;
				}
			}
			catch (final Exception e)
			{
				if (tx != null)
				{
					try
					{
						XAManager.rollback(tx);
					}
					catch(Exception f)
					{
						HRDBMSWorker.logger.error("", f);
					}
				}
				
				clientConnection = false;
				try
				{
					sock.close();
					if (worker != null)
					{
						ArrayList<Object> cmd2 = new ArrayList<Object>(1);
						cmd2.add("CLOSE");
						worker.in.put(cmd2);
					}
					this.terminate();
				}
				catch(Exception f)
				{
					if (worker != null)
					{
						ArrayList<Object> cmd2 = new ArrayList<Object>(1);
						cmd2.add("CLOSE");
						while (true)
						{
							try
							{
								worker.in.put(cmd2);
								break;
							}
							catch(Exception g)
							{}
						}
					}
					this.terminate();
				}
			}
		}
		
		int iso = this.bytesToInt(arg);
		if (tx == null)
		{
			tx = new Transaction(Transaction.ISOLATION_CS);
		}
		tx.setIsolationLevel(iso);
	}
	
	private void testConnection()
	{
		sendOK();
	}
	
	private void setSchema()
	{
		byte[] arg = new byte[4];
		int count = 0;
		while (count < 4)
		{
			try
			{
				int temp = sock.getInputStream().read(arg, count, 4 - count);
				if (temp == -1)
				{
					if (tx != null)
					{
						XAManager.rollback(tx);
					}
					
					clientConnection = false;
					try
					{
						sock.close();
						if (worker != null)
						{
							ArrayList<Object> cmd2 = new ArrayList<Object>(1);
							cmd2.add("CLOSE");
							worker.in.put(cmd2);
						}
						this.terminate();
					}
					catch(Exception f)
					{
						if (worker != null)
						{
							ArrayList<Object> cmd2 = new ArrayList<Object>(1);
							cmd2.add("CLOSE");
							worker.in.put(cmd2);
						}
						this.terminate();
					}
				}
				else
				{
					count += temp;
				}
			}
			catch (final Exception e)
			{
				if (tx != null)
				{
					try
					{
						XAManager.rollback(tx);
					}
					catch(Exception f)
					{
						HRDBMSWorker.logger.error("", f);
					}
				}
				
				clientConnection = false;
				try
				{
					sock.close();
					if (worker != null)
					{
						ArrayList<Object> cmd2 = new ArrayList<Object>(1);
						cmd2.add("CLOSE");
						worker.in.put(cmd2);
					}
					this.terminate();
				}
				catch(Exception f)
				{
					if (worker != null)
					{
						ArrayList<Object> cmd2 = new ArrayList<Object>(1);
						cmd2.add("CLOSE");
						while (true)
						{
							try
							{
								worker.in.put(cmd2);
								break;
							}
							catch(Exception g)
							{}
						}
					}
					this.terminate();
				}
			}
		}
		
		int length = this.bytesToInt(arg);
		//read schema string
		arg = new byte[length];
		read(arg);
		try
		{
			String schema = new String(arg, "UTF-8");
			MetaData.setDefaultSchema(this, schema);
		}
		catch(Exception e)
		{}
		
		
	}
	
	protected void terminate()
	{
		MetaData.removeDefaultSchema(this);
		super.terminate();
	}
	
	private void read(byte[] arg)
	{
		int count = 0;
		while (count < arg.length)
		{
			try
			{
				int temp = sock.getInputStream().read(arg, count, arg.length - count);
				if (temp == -1)
				{
					if (tx != null)
					{
						XAManager.rollback(tx);
					}
					
					clientConnection = false;
					try
					{
						sock.close();
						if (worker != null)
						{
							ArrayList<Object> cmd2 = new ArrayList<Object>(1);
							cmd2.add("CLOSE");
							worker.in.put(cmd2);
						}
						this.terminate();
					}
					catch(Exception f)
					{
						if (worker != null)
						{
							ArrayList<Object> cmd2 = new ArrayList<Object>(1);
							cmd2.add("CLOSE");
							worker.in.put(cmd2);
						}
						this.terminate();
					}
				}
				else
				{
					count += temp;
				}
			}
			catch (final Exception e)
			{
				if (tx != null)
				{
					try
					{
						XAManager.rollback(tx);
					}
					catch(Exception f)
					{
						HRDBMSWorker.logger.error("", f);
					}
				}
				
				clientConnection = false;
				try
				{
					sock.close();
					if (worker != null)
					{
						ArrayList<Object> cmd2 = new ArrayList<Object>(1);
						cmd2.add("CLOSE");
						worker.in.put(cmd2);
					}
					this.terminate();
				}
				catch(Exception f)
				{
					if (worker != null)
					{
						ArrayList<Object> cmd2 = new ArrayList<Object>(1);
						cmd2.add("CLOSE");
						while (true)
						{
							try
							{
								worker.in.put(cmd2);
								break;
							}
							catch(Exception g)
							{}
						}
					}
					this.terminate();
				}
			}
		}
	}
	
	private void getSchema()
	{
		String schema = new MetaData(this).getCurrentSchema();
		sendString(schema);
	}
	
	private void sendString(String string)
	{
		try
		{
			byte[] bytes = string.getBytes("UTF-8");
			sock.getOutputStream().write(intToBytes(bytes.length));
			sock.getOutputStream().write(bytes);
			sock.getOutputStream().flush();
		}
		catch(Exception e)
		{
			if (tx != null)
			{
				try
				{
					XAManager.rollback(tx);
				}
				catch(Exception f)
				{
					HRDBMSWorker.logger.error("", f);
				}
			}
			
			clientConnection = false;
			try
			{
				sock.close();
				if (worker != null)
				{
					ArrayList<Object> cmd2 = new ArrayList<Object>(1);
					cmd2.add("CLOSE");
					worker.in.put(cmd2);
				}
				this.terminate();
			}
			catch(Exception f)
			{
				if (worker != null)
				{
					ArrayList<Object> cmd2 = new ArrayList<Object>(1);
					cmd2.add("CLOSE");
					while (true)
					{
						try
						{
							worker.in.put(cmd2);
							break;
						}
						catch(Exception g)
						{}
					}
				}
				this.terminate();
			}
		}
	}
	
	private void executeQuery()
	{
		byte[] bLen = new byte[4];
		read(bLen);
		byte[] bStmt = new byte[bytesToInt(bLen)];
		read(bStmt);
		String sql = null;
		try
		{
			sql = new String(bStmt, "UTF-8");
		}
		catch(Exception e)
		{}
		
		if (tx == null)
		{
			tx = new Transaction(Transaction.ISOLATION_CS);
		}
		
		try
		{
			worker = XAManager.executeQuery(sql, tx, this);
			HRDBMSWorker.addThread(worker);
			this.sendOK();
		}
		catch(Exception e)
		{
			this.sendNo();
			returnExceptionToClient(e);
		}
	}
	
	private void returnExceptionToClient(Exception e)
	{
		byte[] text = null;
		try
		{
			text = e.getMessage().getBytes("UTF-8");
		}
		catch(Exception f)
		{}
		
		byte[] textLen = intToBytes(text.length);
		try
		{
			sock.getOutputStream().write(textLen);
			sock.getOutputStream().write(text);
			sock.getOutputStream().flush();
		}
		catch(Exception f)
		{
			if (tx != null)
			{
				try
				{
					XAManager.rollback(tx);
				}
				catch(Exception g)
				{
					HRDBMSWorker.logger.error("", g);
				}
			}
			
			clientConnection = false;
			try
			{
				sock.close();
				if (worker != null)
				{
					ArrayList<Object> cmd2 = new ArrayList<Object>(1);
					cmd2.add("CLOSE");
					worker.in.put(cmd2);
				}
				this.terminate();
			}
			catch(Exception h)
			{
				if (worker != null)
				{
					ArrayList<Object> cmd2 = new ArrayList<Object>(1);
					cmd2.add("CLOSE");
					while (true)
					{
						try
						{
							worker.in.put(cmd2);
							break;
						}
						catch(Exception g)
						{}
					}
				}
				this.terminate();
			}
		}
	}
	
	private void getRSMeta()
	{
		try
		{
			if (worker == null)
			{
				closeConnection();
				this.terminate();
				return;
			}
		
			ArrayList<Object> cmd2 = new ArrayList<Object>(1);
			cmd2.add("META");
			while (true)
			{
				try
				{
					worker.in.put(cmd2);
					break;
				}
				catch(Exception g)
				{}
			}
		
			ArrayList<Object> buffer = new ArrayList<Object>();
			Object obj = worker.out.take();
			if (!(obj instanceof HashMap))
			{
				buffer.add(obj);
				obj = worker.out.take();
				while (!(obj instanceof HashMap))
				{
					buffer.add(obj);
					obj = worker.out.take();
				}
			}
		
			ObjectOutputStream objOut = new ObjectOutputStream(sock.getOutputStream());
			objOut.writeObject(obj);
			obj = worker.out.take();
			objOut.writeObject(obj);
			obj = worker.out.take();
			objOut.writeObject(obj);
			objOut.flush();
			objOut.close();
		
			obj = worker.out.peek();
			if (obj != null)
			{
				cmd2 = new ArrayList<Object>(1);
				cmd2.add("CLOSE");
				while (true)
				{
					try
					{
						worker.in.put(cmd2);
						break;
					}
					catch(Exception g)
					{}
				}
				
				return;
			}
		
			if (buffer.size() > 0)
			{
				for (Object o : buffer)
				{
					worker.out.put(o);
				}	
			}
		}
		catch(Exception e)
		{
			ArrayList<Object> cmd2 = new ArrayList<Object>(1);
			cmd2.add("CLOSE");
			while (true)
			{
				try
				{
					worker.in.put(cmd2);
					break;
				}
				catch(Exception g)
				{}
			}
		}
	}
	
	private void closeRS()
	{
		if (worker == null)
		{
			return;
		}
		
		ArrayList<Object> al = new ArrayList<Object>(1);
		al.add("CLOSE");
		while (true)
		{
			try
			{
				worker.in.put(al);
				break;
			}
			catch(InterruptedException e)
			{}
		}
	}
	
	private void doNext()
	{
		ArrayList<Object> al = new ArrayList<Object>();
		al.add("NEXT");
		
		byte[] arg = new byte[4];
		int count = 0;
		while (count < 4)
		{
			try
			{
				int temp = sock.getInputStream().read(arg, count, 4 - count);
				if (temp == -1)
				{
					if (tx != null)
					{
						XAManager.rollback(tx);
					}
					
					clientConnection = false;
					try
					{
						sock.close();
						if (worker != null)
						{
							ArrayList<Object> cmd2 = new ArrayList<Object>(1);
							cmd2.add("CLOSE");
							worker.in.put(cmd2);
						}
						this.terminate();
					}
					catch(Exception f)
					{
						if (worker != null)
						{
							ArrayList<Object> cmd2 = new ArrayList<Object>(1);
							cmd2.add("CLOSE");
							worker.in.put(cmd2);
						}
						this.terminate();
					}
				}
				else
				{
					count += temp;
				}
			}
			catch (final Exception e)
			{
				if (tx != null)
				{
					try
					{
						XAManager.rollback(tx);
					}
					catch(Exception f)
					{
						HRDBMSWorker.logger.error("", f);
					}
				}
				
				clientConnection = false;
				try
				{
					sock.close();
					if (worker != null)
					{
						ArrayList<Object> cmd2 = new ArrayList<Object>(1);
						cmd2.add("CLOSE");
						worker.in.put(cmd2);
					}
					this.terminate();
				}
				catch(Exception f)
				{
					if (worker != null)
					{
						ArrayList<Object> cmd2 = new ArrayList<Object>(1);
						cmd2.add("CLOSE");
						while (true)
						{
							try
							{
								worker.in.put(cmd2);
								break;
							}
							catch(Exception g)
							{}
						}
					}
					this.terminate();
				}
			}
		}
		
		int num = this.bytesToInt(arg);
		al.add(num);
		
		while (true)
		{
			try
			{
				worker.in.put(al);
				break;
			}
			catch(InterruptedException e)
			{}
		}
		
		ArrayList<Object> buffer = new ArrayList<Object>(num);
		while (num > 0)
		{
			Object obj = null;
			while (true)
			{
				try
				{
					worker.out.take();
					break;
				}
				catch(InterruptedException e)
				{}
			}
			
			buffer.add(obj);
			if (obj instanceof Exception || obj instanceof DataEndMarker)
			{
				break;
			}
			
			num--;
		}
		
		
		Object obj = buffer.get(buffer.size() - 1);
		if (obj instanceof Exception)
		{
			sendNo();
			returnExceptionToClient((Exception)obj);
			return;
		}
		
		sendOK();
		try
		{
			for (Object obj2 : buffer)
			{
				byte[] data = toBytes((ArrayList<Object>)obj2);
				sock.getOutputStream().write(data);
			}
		
			sock.getOutputStream().flush();
		}
		catch(Exception e)
		{
			if (tx != null)
			{
				try
				{
					XAManager.rollback(tx);
				}
				catch(Exception f)
				{
					HRDBMSWorker.logger.error("", f);
				}
			}
			
			clientConnection = false;
			try
			{
				sock.close();
				if (worker != null)
				{
					ArrayList<Object> cmd2 = new ArrayList<Object>(1);
					cmd2.add("CLOSE");
					worker.in.put(cmd2);
				}
				this.terminate();
			}
			catch(Exception f)
			{
				if (worker != null)
				{
					ArrayList<Object> cmd2 = new ArrayList<Object>(1);
					cmd2.add("CLOSE");
					while (true)
					{
						try
						{
							worker.in.put(cmd2);
							break;
						}
						catch(Exception g)
						{}
					}
				}
				this.terminate();
			}
		}
	}
	
	private final byte[] toBytes(Object v) throws Exception
	{
		ArrayList<Object> val;
		if (v instanceof ArrayList)
		{
			val = (ArrayList<Object>)v;
		}
		else
		{
			final byte[] retval = new byte[9];
			retval[0] = 0;
			retval[1] = 0;
			retval[2] = 0;
			retval[3] = 5;
			retval[4] = 0;
			retval[5] = 0;
			retval[6] = 0;
			retval[7] = 1;
			retval[8] = 5;
			return retval;
		}

		int size = val.size() + 8;
		final byte[] header = new byte[size];
		int i = 8;
		for (final Object o : val)
		{
			if (o instanceof Long)
			{
				header[i] = (byte)0;
				size += 8;
			}
			else if (o instanceof Integer)
			{
				header[i] = (byte)1;
				size += 4;
			}
			else if (o instanceof Double)
			{
				header[i] = (byte)2;
				size += 8;
			}
			else if (o instanceof MyDate)
			{
				header[i] = (byte)3;
				size += 8;
			}
			else if (o instanceof String)
			{
				header[i] = (byte)4;
				size += (4 + ((String)o).length());
			}
			else if (o instanceof AtomicLong)
			{
				header[i] = (byte)6;
				size += 8;
			}
			else if (o instanceof AtomicDouble)
			{
				header[i] = (byte)7;
				size += 8;
			}
			else if (o instanceof ArrayList)
			{
				if (((ArrayList)o).size() != 0)
				{
					Exception e = new Exception();
					HRDBMSWorker.logger.error("Non-zero size ArrayList in toBytes()", e);
					throw new Exception("Non-zero size ArrayList in toBytes()");
				}
				header[i] = (byte)8;
			}
			else if (o == null)
			{
				header[i] = (byte)9;
			}
			else
			{
				HRDBMSWorker.logger.error("Unknown type " + o.getClass() + " in toBytes()");
				HRDBMSWorker.logger.error(o);
				throw new Exception("Unknown type " + o.getClass() + " in toBytes()");
			}

			i++;
		}

		final byte[] retval = new byte[size];
		// System.out.println("In toBytes(), row has " + val.size() +
		// " columns, object occupies " + size + " bytes");
		System.arraycopy(header, 0, retval, 0, header.length);
		i = 8;
		final ByteBuffer retvalBB = ByteBuffer.wrap(retval);
		retvalBB.putInt(size - 4);
		retvalBB.putInt(val.size());
		retvalBB.position(header.length);
		for (final Object o : val)
		{
			if (retval[i] == 0)
			{
				retvalBB.putLong((Long)o);
			}
			else if (retval[i] == 1)
			{
				retvalBB.putInt((Integer)o);
			}
			else if (retval[i] == 2)
			{
				retvalBB.putDouble((Double)o);
			}
			else if (retval[i] == 3)
			{
				retvalBB.putLong(((MyDate)o).getTime());
			}
			else if (retval[i] == 4)
			{
				byte[] temp = null;
				try
				{
					temp = ((String)o).getBytes("UTF-8");
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					throw e;
				}
				retvalBB.putInt(temp.length);
				retvalBB.put(temp);
			}
			else if (retval[i] == 6)
			{
				retvalBB.putLong(((AtomicLong)o).get());
			}
			else if (retval[i] == 7)
			{
				retvalBB.putDouble(((AtomicDouble)o).get());
			}
			else if (retval[i] == 8)
			{
			}
			else if (retval[i] == 9)
			{
			}

			i++;
		}

		return retval;
	}
	
	private void readNonCoord(byte[] arg) throws Exception
	{
		int count = 0;
		while (count < arg.length)
		{
			int temp = sock.getInputStream().read(arg, count, arg.length - count);
			if (temp == -1)
			{
				throw new Exception("Hit end of stream when reading from socket");
			}
			else
			{
				count += temp;
			}
		}
	}
	
	private long bytesToLong(byte[] val)
	{
		final long ret = java.nio.ByteBuffer.wrap(val).getLong();
		return ret;
	}
	
	private void removeFromTree(String host, ArrayList<Object> tree, ArrayList<Object> parent)
	{
		for (Object o : tree)
		{
			if (o instanceof String)
			{
				if (((String)o).equals(host))
				{
					tree.remove(host);
					if (tree.size() == 0 && parent != null)
					{
						parent.remove(tree);
					}
					return;
				}
			}
			else
			{
				removeFromTree(host, (ArrayList<Object>)o, tree);
			}
		}
	}
	
	private void localRollback()
	{
		ArrayList<Object> tree = null;
		byte[] txBytes = new byte[8];
		long txNum = -1;
		try
		{
			ObjectInputStream objIn = new ObjectInputStream(sock.getInputStream());
			readNonCoord(txBytes);
			txNum = bytesToLong(txBytes);
			tree = (ArrayList<Object>)objIn.readObject();
		}
		catch(Exception e)
		{
			sendNo();
			return;
		}
		
		sendOK();
		Transaction tx = new Transaction(txNum);
		try
		{
			tx.rollback();
		}
		catch(Exception e)
		{
			queueCommandSelf("ROLLBACK", tx);
			blackListSelf();
		}
		
		Object obj = tree.get(0);
		while (obj instanceof ArrayList)
		{
			obj = ((ArrayList)obj).get(0);
		}
		
		removeFromTree((String)obj, tree, null); //also delete parents if now empty
		
		for (Object o : tree)
		{
			new SendRollbackThread(o, tx).start();
		}
	}
	
	private static class SendRollbackThread extends HRDBMSThread
	{
		private Object o;
		private Transaction tx;
		
		public SendRollbackThread(Object o, Transaction tx)
		{
			this.o = o;
			this.tx = tx;
		}
		
		public void run()
		{
			if (o instanceof String)
			{
				Socket sock = null;
				try
				{
					sock = new Socket((String)o, Integer.parseInt(HRDBMSWorker.getHParms().getProperty("port_number")));
					OutputStream out = sock.getOutputStream();
					byte[] outMsg = "LROLLBCK        ".getBytes("UTF-8");
					outMsg[8] = 0;
					outMsg[9] = 0;
					outMsg[10] = 0;
					outMsg[11] = 0;
					outMsg[12] = 0;
					outMsg[13] = 0;
					outMsg[14] = 0;
					outMsg[15] = 0;
					out.write(outMsg);
					out.write(longToBytes(tx.number()));
					ObjectOutputStream objOut = new ObjectOutputStream(out);
					ArrayList<Object> alo = new ArrayList<Object>(1);
					alo.add(o);
					objOut.writeObject(alo);
					objOut.flush();
					out.flush();
					objOut.close();
					getConfirmation(sock);
					out.close();
					sock.close();
				}
				catch(Exception e)
				{
					blackListByHost((String)o);
					queueCommandByHost((String)o, "ROLLBACK", tx);
				}
			}
			else if (((ArrayList<Object>)o).size() > 0)
			{
				Socket sock = null;
				Object obj2 = ((ArrayList<Object>)o).get(0);
				while (obj2 instanceof ArrayList)
				{
					obj2 = ((ArrayList<Object>)obj2).get(0);
				}
				
				String hostname = (String)obj2;
				try
				{
					sock = new Socket(hostname, Integer.parseInt(HRDBMSWorker.getHParms().getProperty("port_number")));
					OutputStream out = sock.getOutputStream();
					byte[] outMsg = "LROLLBCK        ".getBytes("UTF-8");
					outMsg[8] = 0;
					outMsg[9] = 0;
					outMsg[10] = 0;
					outMsg[11] = 0;
					outMsg[12] = 0;
					outMsg[13] = 0;
					outMsg[14] = 0;
					outMsg[15] = 0;
					out.write(outMsg);
					out.write(longToBytes(tx.number()));
					ObjectOutputStream objOut = new ObjectOutputStream(out);
					objOut.writeObject((ArrayList<Object>)o);
					objOut.flush();
					out.flush();
					objOut.close();
					getConfirmation(sock);
					out.close();
					sock.close();
				}
				catch(Exception e)
				{
					sock.close();
					blackListByHost((String)obj2);
					queueCommandByHost((String)obj2, "ROLLBACK", tx);
					//retry others
					boolean toDo = rebuildTree((ArrayList<Object>)o, (String)obj2);
					if (toDo)
					{
						sendRollback((ArrayList<Object>)o, tx);
					}
				}
			}
		}
	}
	
	private static byte[] longToBytes(long val)
	{
		final byte[] buff = new byte[8];
		buff[0] = (byte)(val >> 56);
		buff[1] = (byte)((val & 0x00FF000000000000L) >> 48);
		buff[2] = (byte)((val & 0x0000FF0000000000L) >> 40);
		buff[3] = (byte)((val & 0x000000FF00000000L) >> 32);
		buff[4] = (byte)((val & 0x00000000FF000000L) >> 24);
		buff[5] = (byte)((val & 0x0000000000FF0000L) >> 16);
		buff[6] = (byte)((val & 0x000000000000FF00L) >> 8);
		buff[7] = (byte)((val & 0x00000000000000FFL));
		return buff;
	}
	
	private static void getConfirmation(Socket sock) throws Exception
	{
		InputStream in = sock.getInputStream();
		byte[] inMsg = new byte[2];
		
		int count = 0;
		while (count < 2)
		{
			try
			{
				int temp = in.read(inMsg, count, 2 - count);
				if (temp == -1)
				{
					throw new Exception();
				}
				else
				{
					count += temp;
				}
			}
			catch (final Exception e)
			{
				throw new Exception();
			}
		}
		
		String inStr = new String(inMsg, "UTF-8");
		if (!inStr.equals("OK"))
		{
			throw new Exception();
		}
	}
	
	private static boolean rebuildTree(ArrayList<Object> tree, String remove)
	{
		ArrayList<String> nodes = new ArrayList<String>();
		for (Object o : tree)
		{
			if (o instanceof String)
			{
				if (!((String)o).equals(remove))
				{
					nodes.add((String)o);
				}
			}
			else
			{
				//ArrayList<Object>
				nodes.addAll(getAllNodes((ArrayList<Object>)o, remove));
			}
		}
		
		if (nodes.size() > 0)
		{
			tree.clear();
			cloneInto(tree, makeTree(nodes));
			return true;
		}
		
		return false;
	}
	
	private static ArrayList<String> getAllNodes(ArrayList<Object> tree, String remove)
	{
		ArrayList<String> retval = new ArrayList<String>();
		for (Object o : tree)
		{
			if (o instanceof String)
			{
				if (!((String)o).equals(remove))
				{
					retval.add((String)o);
				}
			}
			else
			{
				//ArrayList<Object>
				retval.addAll(getAllNodes((ArrayList<Object>)o, remove));
			}
		}
		
		return retval;
	}
	
	private static ArrayList<Object> makeTree(ArrayList<String> nodes)
	{
		int max = Integer.parseInt(HRDBMSWorker.getHParms().getProperty("max_neighbor_nodes"));
		if (nodes.size() <= max)
		{
			ArrayList<Object> retval = new ArrayList<Object>(nodes);
			return retval;
		}
		
		ArrayList<Object> retval = new ArrayList<Object>();
		int i = 0;
		while (i < max)
		{
			retval.add(nodes.get(i));
			i++;
		}
		
		int remaining = nodes.size() - i;
		int perNode = remaining / max + 1;
		
		int j = 0;
		while (i < nodes.size())
		{
			String first = (String)retval.get(j);
			retval.remove(j);
			ArrayList<String> list = new ArrayList<String>(perNode+1);
			list.add(first);
			int k = 0;
			while (k < perNode && i < nodes.size())
			{
				list.add(nodes.get(i));
				i++;
				k++;
			}
			
			retval.add(j, list);
			j++;
		}
		
		if (((ArrayList<String>)retval.get(0)).size() <= max)
		{
			return retval;
		}
		
		//more than 2 tier
		i = 0;
		while (i < retval.size())
		{
			ArrayList<String> list = (ArrayList<String>)retval.remove(i);
			retval.add(i, makeTree(list));
			i++;
		}
		
		return retval;
	}
	
	private static void cloneInto(ArrayList<Object> target, ArrayList<Object> source)
	{
		for (Object o : source)
		{
			target.add(o);
		}
	}
	
	private static void sendRollback(ArrayList<Object> tree, Transaction tx)
	{
		Object obj = tree.get(0);
		while (obj instanceof ArrayList)
		{
			obj = ((ArrayList)obj).get(0);
		}
		
		String hostname = (String)obj;
		Socket sock = null;
		try
		{
			sock = new Socket(hostname, Integer.parseInt(HRDBMSWorker.getHParms().getProperty("port_number")));
			OutputStream out = sock.getOutputStream();
			byte[] outMsg = "LROLLBCK        ".getBytes("UTF-8");
			outMsg[8] = 0;
			outMsg[9] = 0;
			outMsg[10] = 0;
			outMsg[11] = 0;
			outMsg[12] = 0;
			outMsg[13] = 0;
			outMsg[14] = 0;
			outMsg[15] = 0;
			out.write(outMsg);
			out.write(longToBytes(tx.number()));
			ObjectOutputStream objOut = new ObjectOutputStream(out);
			objOut.writeObject(tree);
			objOut.flush();
			out.flush();
			objOut.close();
			getConfirmation(sock);
			out.close();
			sock.close();
		}
		catch(Exception e)
		{
			sock.close();
			blackListByHost((String)obj);
			queueCommandByHost((String)obj, "ROLLBACK", tx);
			boolean toDo = rebuildTree(tree, (String)obj);
			if (toDo)
			{
				sendRollback(tree, tx);
			}
		}
	}
	
	private void localCommit()
	{
		ArrayList<Object> tree = null;
		byte[] txBytes = new byte[8];
		long txNum = -1;
		try
		{
			ObjectInputStream objIn = new ObjectInputStream(sock.getInputStream());
			readNonCoord(txBytes);
			txNum = bytesToLong(txBytes);
			tree = (ArrayList<Object>)objIn.readObject();
		}
		catch(Exception e)
		{
			sendNo();
			return;
		}
		
		sendOK();
		Transaction tx = new Transaction(txNum);
		try
		{
			tx.commit();
		}
		catch(Exception e)
		{
			queueCommandSelf("COMMIT", tx);
			blackListSelf();
		}
		
		Object obj = tree.get(0);
		while (obj instanceof ArrayList)
		{
			obj = ((ArrayList)obj).get(0);
		}
		
		removeFromTree((String)obj, tree, null); //also delete parents if now empty
		
		for (Object o : tree)
		{
			new SendCommitThread(o, tx).start();
		}
	}
	
	private static class SendCommitThread extends HRDBMSThread
	{
		private Object o;
		private Transaction tx;
		
		public SendCommitThread(Object o, Transaction tx)
		{
			this.o = o;
			this.tx= tx;
		}
		
		public void run()
		{
			if (o instanceof String)
			{
				Socket sock = null;
				try
				{
					sock = new Socket((String)o, Integer.parseInt(HRDBMSWorker.getHParms().getProperty("port_number")));
					OutputStream out = sock.getOutputStream();
					byte[] outMsg = "LCOMMIT         ".getBytes("UTF-8");
					outMsg[8] = 0;
					outMsg[9] = 0;
					outMsg[10] = 0;
					outMsg[11] = 0;
					outMsg[12] = 0;
					outMsg[13] = 0;
					outMsg[14] = 0;
					outMsg[15] = 0;
					out.write(outMsg);
					out.write(longToBytes(tx.number()));
					ObjectOutputStream objOut = new ObjectOutputStream(out);
					ArrayList<Object> alo = new ArrayList<Object>(1);
					alo.add(o);
					objOut.writeObject(alo);
					objOut.flush();
					out.flush();
					objOut.close();
					getConfirmation(sock);
					out.close();
					sock.close();
				}
				catch(Exception e)
				{
					blackListByHost((String)o);
					queueCommandByHost((String)o, "COMMIT", tx);
				}
			}
			else if (((ArrayList<Object>)o).size() > 0)
			{
				Socket sock = null;
				Object obj2 = ((ArrayList<Object>)o).get(0);
				while (obj2 instanceof ArrayList)
				{
					obj2 = ((ArrayList<Object>)obj2).get(0);
				}
				
				String hostname = (String)obj2;
				try
				{
					sock = new Socket(hostname, Integer.parseInt(HRDBMSWorker.getHParms().getProperty("port_number")));
					OutputStream out = sock.getOutputStream();
					byte[] outMsg = "LCOMMIT         ".getBytes("UTF-8");
					outMsg[8] = 0;
					outMsg[9] = 0;
					outMsg[10] = 0;
					outMsg[11] = 0;
					outMsg[12] = 0;
					outMsg[13] = 0;
					outMsg[14] = 0;
					outMsg[15] = 0;
					out.write(outMsg);
					out.write(longToBytes(tx.number()));
					ObjectOutputStream objOut = new ObjectOutputStream(out);
					objOut.writeObject((ArrayList<Object>)o);
					objOut.flush();
					out.flush();
					objOut.close();
					getConfirmation(sock);
					out.close();
					sock.close();
				}
				catch(Exception e)
				{
					sock.close();
					blackListByHost((String)obj2);
					queueCommandByHost((String)obj2, "COMMIT", tx);
					//retry others
					boolean toDo = rebuildTree((ArrayList<Object>)o, (String)obj2);
					if (toDo)
					{
						sendCommit((ArrayList<Object>)o, tx);
					}
				}
			}
		}
	}
	
	private static void sendCommit(ArrayList<Object> tree, Transaction tx)
	{
		Object obj = tree.get(0);
		while (obj instanceof ArrayList)
		{
			obj = ((ArrayList)obj).get(0);
		}
		
		String hostname = (String)obj;
		Socket sock = null;
		try
		{
			sock = new Socket(hostname, Integer.parseInt(HRDBMSWorker.getHParms().getProperty("port_number")));
			OutputStream out = sock.getOutputStream();
			byte[] outMsg = "LCOMMIT         ".getBytes("UTF-8");
			outMsg[8] = 0;
			outMsg[9] = 0;
			outMsg[10] = 0;
			outMsg[11] = 0;
			outMsg[12] = 0;
			outMsg[13] = 0;
			outMsg[14] = 0;
			outMsg[15] = 0;
			out.write(outMsg);
			out.write(longToBytes(tx.number()));
			ObjectOutputStream objOut = new ObjectOutputStream(out);
			objOut.writeObject(tree);
			objOut.flush();
			out.flush();
			objOut.close();
			getConfirmation(sock);
			out.close();
			sock.close();
		}
		catch(Exception e)
		{
			sock.close();
			blackListByHost((String)obj);
			queueCommandByHost((String)obj, "COMMIT", tx);
			boolean toDo = rebuildTree(tree, (String)obj);
			if (toDo)
			{
				sendCommit(tree, tx);
			}
		}
	}
	
	private void prepare()
	{
		ArrayList<Object> tree = null;
		byte[] txBytes = new byte[8];
		long txNum = -1;
		byte[] lenBytes = new byte[4];
		int length = -1;
		String host = null;
		byte[] data = null;
		try
		{
			ObjectInputStream objIn = new ObjectInputStream(sock.getInputStream());
			readNonCoord(txBytes);
			txNum = bytesToLong(txBytes);
			readNonCoord(lenBytes);
			length = bytesToInt(lenBytes);
			data = new byte[length];
			readNonCoord(data);
			host = new String(data, "UTF-8");
			tree = (ArrayList<Object>)objIn.readObject();
		}
		catch(Exception e)
		{
			sendNo();
			return;
		}
		
		Transaction tx = new Transaction(txNum);
		try
		{
			tx.tryCommit(host);
		}
		catch(Exception e)
		{
			sendNo();
			return;
		}
		
		Object obj = tree.get(0);
		while (obj instanceof ArrayList)
		{
			obj = ((ArrayList)obj).get(0);
		}
		
		removeFromTree((String)obj, tree, null); //also delete parents if now empty
		
		ArrayList<SendPrepareThread> threads = new ArrayList<SendPrepareThread>();
		for (Object o : tree)
		{
			threads.add(new SendPrepareThread(o, tx, lenBytes, data));
		}
		
		for (SendPrepareThread thread : threads)
		{
			thread.start();
		}
		
		boolean allOK = true;
		for (SendPrepareThread thread : threads)
		{
			while (true)
			{
				try
				{
					thread.join();
					break;
				}
				catch(InterruptedException e)
				{}
			}
			if (!thread.sendOK())
			{
				allOK = false;
			}
		}
		
		if (allOK)
		{
			sendOK();
		}
		else
		{
			sendNo();
			try
			{
				sock.close();
			}
			catch(Exception f)
			{}
		}
	}
	
	private static class SendPrepareThread extends HRDBMSThread
	{
		private Object o;
		private Transaction tx;
		private boolean sendOK;
		private byte[] lenBytes;
		private byte[] data;
		
		public SendPrepareThread(Object o, Transaction tx, byte[] lenBytes, byte[] data)
		{
			this.o = o;
			this.tx = tx;
			this.lenBytes = lenBytes;
			this.data = data;
		}
		
		public boolean sendOK()
		{
			return sendOK;
		}
		
		public void run()
		{
			if (o instanceof String)
			{
				Socket sock = null;
				try
				{
					sock = new Socket((String)o, Integer.parseInt(HRDBMSWorker.getHParms().getProperty("port_number")));
					OutputStream out = sock.getOutputStream();
					byte[] outMsg = "PREPARE         ".getBytes("UTF-8");
					outMsg[8] = 0;
					outMsg[9] = 0;
					outMsg[10] = 0;
					outMsg[11] = 0;
					outMsg[12] = 0;
					outMsg[13] = 0;
					outMsg[14] = 0;
					outMsg[15] = 0;
					out.write(outMsg);
					out.write(longToBytes(tx.number()));
					out.write(lenBytes);
					out.write(data);
					ObjectOutputStream objOut = new ObjectOutputStream(out);
					ArrayList<Object> alo = new ArrayList<Object>(1);
					alo.add(o);
					objOut.writeObject(alo);
					objOut.flush();
					out.flush();
					objOut.close();
					getConfirmation(sock);
					out.close();
					sock.close();
				}
				catch(Exception e)
				{
					sendOK = false;
					return;
				}
				sendOK = true;
			}
			else if (((ArrayList<Object>)o).size() > 0)
			{
				Socket sock = null;
				Object obj2 = ((ArrayList<Object>)o).get(0);
				while (obj2 instanceof ArrayList)
				{
					obj2 = ((ArrayList<Object>)obj2).get(0);
				}
				
				String hostname = (String)obj2;
				try
				{
					sock = new Socket(hostname, Integer.parseInt(HRDBMSWorker.getHParms().getProperty("port_number")));
					OutputStream out = sock.getOutputStream();
					byte[] outMsg = "PREPARE         ".getBytes("UTF-8");
					outMsg[8] = 0;
					outMsg[9] = 0;
					outMsg[10] = 0;
					outMsg[11] = 0;
					outMsg[12] = 0;
					outMsg[13] = 0;
					outMsg[14] = 0;
					outMsg[15] = 0;
					out.write(outMsg);
					out.write(longToBytes(tx.number()));
					out.write(lenBytes);
					out.write(data);
					ObjectOutputStream objOut = new ObjectOutputStream(out);
					objOut.writeObject((ArrayList<Object>)o);
					objOut.flush();
					out.flush();
					objOut.close();
					getConfirmation(sock);
					out.close();
					sock.close();
				}
				catch(Exception e)
				{
					sendOK = false;
					return;
				}
				
				sendOK = true;
			}
		}
	}
	
	private void checkTx()
	{
		byte[] data = new byte[8];
		try
		{
			readNonCoord(data);
		}
		catch(Exception e)
		{
			try
			{
				sock.close();
			}
			catch(Exception f)
			{
				try
				{
					sock.getOutputStream().close();
				}
				catch(Exception g)
				{}
			}
			return;
		}
		long txnum = bytesToLong(data); 
		String filename = HRDBMSWorker.getHParms().getProperty("log_dir");
		if (!filename.endsWith("/"))
		{
			filename += "/";
		}
		filename += "xa.log";
		Iterator<LogRec> iter = LogManager.iterator(filename);
		while (iter.hasNext())
		{
			LogRec rec = iter.next();
			if (rec.txnum() == txnum)
			{
				if (rec.type() == LogRec.XACOMMIT)
				{
					sendOK();
				}
				else if (rec.type() == LogRec.XAABORT)
				{
					sendNo();
				}
				else if (rec.type() == LogRec.PREPARE)
				{
					sendNo();
				}
			}
		}
	}
	
	private void massDelete()
	{
		ArrayList<Object> tree = null;
		byte[] txBytes = new byte[8];
		long txNum = -1;
		byte[] schemaLenBytes = new byte[4];
		byte[] tableLenBytes = new byte[4];
		int schemaLength = -1;
		int tableLength = -1;
		String schema = null;
		String table = null;
		byte[] schemaData = null;
		byte[] tableData = null;
		ArrayList<String> indexes;
		ArrayList<ArrayList<String>> keys;
		ArrayList<ArrayList<String>> types;
		ArrayList<ArrayList<Boolean>> orders;
		try
		{
			ObjectInputStream objIn = new ObjectInputStream(sock.getInputStream());
			readNonCoord(txBytes);
			txNum = bytesToLong(txBytes);
			readNonCoord(schemaLenBytes);
			schemaLength = bytesToInt(schemaLenBytes);
			schemaData = new byte[schemaLength];
			readNonCoord(schemaData);
			schema = new String(schemaData, "UTF-8");
			readNonCoord(tableLenBytes);
			tableLength = bytesToInt(tableLenBytes);
			tableData = new byte[tableLength];
			readNonCoord(tableData);
			table = new String(tableData, "UTF-8");
			tree = (ArrayList<Object>)objIn.readObject();
			indexes = (ArrayList<String>)objIn.readObject();
			keys = (ArrayList<ArrayList<String>>)objIn.readObject();
			types = (ArrayList<ArrayList<String>>)objIn.readObject();
			orders = (ArrayList<ArrayList<Boolean>>)objIn.readObject();
		}
		catch(Exception e)
		{
			sendNo();
			return;
		}
		
		Object obj = tree.get(0);
		while (obj instanceof ArrayList)
		{
			obj = ((ArrayList)obj).get(0);
		}
		
		removeFromTree((String)obj, tree, null); //also delete parents if now empty
		
		ArrayList<SendMassDeleteThread> threads = new ArrayList<SendMassDeleteThread>();
		for (Object o : tree)
		{
			if (!(o instanceof ArrayList))
			{
				ArrayList<Object> o2 = new ArrayList<Object>();
				o2.add((String)o);
				o = o2;
			}
			threads.add(new SendMassDeleteThread((ArrayList<Object>)o, tx, schema, table, keys, types, orders, indexes));
		}
		
		for (SendMassDeleteThread thread : threads)
		{
			thread.start();
		}
		
		boolean allOK = true;
		for (SendMassDeleteThread thread : threads)
		{
			while (true)
			{
				try
				{
					thread.join();
					break;
				}
				catch(InterruptedException e)
				{}
			}
			if (!thread.getOK())
			{
				allOK = false;
			}
		}
		
		if (allOK)
		{
			Transaction tx = new Transaction(txNum);
			try
			{
				//mass delete table and indexes
				File[] dirs = getDirs(HRDBMSWorker.getHParms().getProperty("data_directories"));
				ArrayList<MassDeleteThread> threads1 = new ArrayList<MassDeleteThread>();
				for (File dir : dirs)
				{
					File dir2 = new File(dir, schema + "." + table + ".tbl");
					threads1.add(new MassDeleteThread(dir2, tx, indexes, keys, types, orders));
				}
				
				for (MassDeleteThread thread : threads1)
				{
					thread.start();
				}
				
				allOK = true;
				for (MassDeleteThread thread : threads1)
				{
					while (true)
					{
						try
						{
							thread.join();
							break;
						}
						catch(Exception e)
						{}
					}
					
					if (!thread.getOK())
					{
						allOK = false;
					}
				}
				
				if (!allOK)
				{
					sendNo();
					return;
				}
				

				sendOK();
				int num = 0;
				for (MassDeleteThread thread : threads1)
				{
					num += thread.getNum();
				}
				sendInt(num);
			}
			catch(Exception e)
			{
				sendNo();
				return;
			}
		}
		else
		{
			sendNo();
			try
			{
				sock.close();
			}
			catch(Exception f)
			{}
		}
	}
	
	private static File[] getDirs(String list)
	{
		StringTokenizer tokens = new StringTokenizer(list, ",", false);
		int i = 0;
		while (tokens.hasMoreTokens())
		{
			tokens.nextToken();
			i++;
		}
		File[] dirs = new File[i];
		tokens = new StringTokenizer(list, ",", false);
		i = 0;
		while (tokens.hasMoreTokens())
		{
			dirs[i] = new File(tokens.nextToken());
			i++;
		}
		
		return dirs;
	}
	
	private static class MassDeleteThread extends HRDBMSThread
	{
		private File file;
		private Transaction tx;
		private int num = 0;
		private boolean ok = true;
		private ArrayList<String> indexes;
		private ArrayList<ArrayList<String>> keys;
		private ArrayList<ArrayList<String>> types;
		private ArrayList<ArrayList<Boolean>> orders;
		
		public MassDeleteThread(File file, Transaction tx, ArrayList<String> indexes, ArrayList<ArrayList<String>> keys, ArrayList<ArrayList<String>> types, ArrayList<ArrayList<Boolean>> orders)
		{
			this.file = file;
			this.tx = tx;
			this.indexes = indexes;
			this.keys = keys;
			this.types = types;
			this.orders = orders;
		}
		
		public int getNum()
		{
			return num;
		}
		
		public boolean getOK()
		{
			return ok;
		}
		
		public void run()
		{
			if (!file.exists())
			{
				return;
			}
			//TODO get length lock
			int numBlocks = (int)(file.length() / Page.BLOCK_SIZE);
			HashMap<Integer, DataType> layout = new HashMap<Integer, DataType>();
			Schema sch = new Schema(layout);
			int onPage = Schema.HEADER_SIZE;
			int lastRequested = Schema.HEADER_SIZE - 1;
			int PREFETCH_REQUEST_SIZE = Integer.parseInt(HRDBMSWorker.getHParms().getProperty("prefetch_request_size")); // 80
			int PAGES_IN_ADVANCE = Integer.parseInt(HRDBMSWorker.getHParms().getProperty("pages_in_advance")); // 40
			while (onPage < numBlocks)
			{
				if (lastRequested - onPage < PAGES_IN_ADVANCE)
				{
					Block[] toRequest = new Block[lastRequested + PREFETCH_REQUEST_SIZE < numBlocks ? PREFETCH_REQUEST_SIZE : numBlocks - lastRequested - 1];
					int i = 0;
					while (i < toRequest.length)
					{
						toRequest[i] = new Block(file.getAbsolutePath(), lastRequested + i + 1);
						i++;
					}
					tx.requestPages(toRequest);
					lastRequested += toRequest.length;
				}
				
				try
				{
					tx.read(new Block(file.getAbsolutePath(), onPage++), sch);
					RowIterator rit = sch.rowIterator();
					while (rit.hasNext())
					{
						Row r = rit.next();
						if (!r.getCol(0).exists())
						{
							continue;
						}
					
						RID rid = r.getRID();
						sch.deleteRow(rid);
						num++;
					}
				}
				catch(Exception e)
				{
					ok = false;
					return;
				}
			}
			
			try
			{
				int i = 0;
				for (String index : indexes)
				{
					Index idx = new Index(new File(file.getParentFile().getAbsoluteFile(), index).getAbsolutePath(), keys.get(i), types.get(i), orders.get(i));
					idx.open();
					idx.massDelete();
					i++;
				}
			}
			catch(Exception e)
			{
				ok = false;
				return;
			}
		}
	}
	
	private class SendMassDeleteThread extends HRDBMSThread
	{
		private ArrayList<Object> tree;
		private Transaction tx;
		private boolean ok;
		int num;
		String schema;
		String table;
		ArrayList<ArrayList<String>> keys;
		ArrayList<ArrayList<String>> types;
		ArrayList<ArrayList<Boolean>> orders;
		ArrayList<String> indexes;
		
		public SendMassDeleteThread(ArrayList<Object> tree, Transaction tx, String schema, String table, ArrayList<ArrayList<String>> keys, ArrayList<ArrayList<String>> types, ArrayList<ArrayList<Boolean>> orders, ArrayList<String> indexes)
		{
			this.tree = tree;
			this.tx = tx;
			this.schema = schema;
			this.table = table;
			this.keys = keys;
			this.types = types;
			this.orders = orders;
			this.indexes = indexes;
		}
		
		public int getNum()
		{
			return num;
		}
		
		public boolean getOK()
		{
			return ok;
		}
		
		private byte[] stringToBytes(String string)
		{
			byte[] data = null;
			try
			{
				data = string.getBytes("UTF-8");
			}
			catch(Exception e)
			{}
			byte[] len = intToBytes(data.length);
			byte[] retval = new byte[data.length + len.length];
			System.arraycopy(len, 0, retval, 0, len.length);
			System.arraycopy(data, 0, retval, len.length, data.length);
			return retval;
		}
		
		public void run()
		{
			sendMassDelete(tree, tx);
		}
		
		private void sendMassDelete(ArrayList<Object> tree, Transaction tx)
		{
			Object obj = tree.get(0);
			while (obj instanceof ArrayList)
			{
				obj = ((ArrayList)obj).get(0);
			}
			
			String hostname = (String)obj;
			Socket sock = null;
			try
			{
				sock = new Socket(hostname, Integer.parseInt(HRDBMSWorker.getHParms().getProperty("port_number")));
				OutputStream out = sock.getOutputStream();
				byte[] outMsg = "MDELETE         ".getBytes("UTF-8");
				outMsg[8] = 0;
				outMsg[9] = 0;
				outMsg[10] = 0;
				outMsg[11] = 0;
				outMsg[12] = 0;
				outMsg[13] = 0;
				outMsg[14] = 0;
				outMsg[15] = 0;
				out.write(outMsg);
				out.write(longToBytes(tx.number()));
				//write schema and table
				out.write(stringToBytes(schema));
				out.write(stringToBytes(table));
				ObjectOutputStream objOut = new ObjectOutputStream(out);
				objOut.writeObject(tree);
				objOut.writeObject(indexes);
				objOut.writeObject(keys);
				objOut.writeObject(types);
				objOut.writeObject(orders);
				objOut.flush();
				out.flush();
				objOut.close();
				getConfirmation(sock);
				int count = 4;
				int off = 0;
				byte[] numBytes = new byte[4];
				while (count > 0)
				{
					int temp = sock.getInputStream().read(numBytes, off, 4-off);
					if (temp == -1)
					{
						ok = false;
						out.close();
						sock.close();
					}
					
					count -= temp;
				}
				
				num = bytesToInt(numBytes);
				out.close();
				sock.close();
				ok = true;
			}
			catch(Exception e)
			{
				ok = false;
				try
				{
					sock.close();
				}
				catch(Exception f)
				{}
			}
		}
	}
	
	private void delete()
	{
		byte[] txBytes = new byte[8];
		long txNum = -1;
		byte[] schemaLenBytes = new byte[4];
		byte[] tableLenBytes = new byte[4];
		int schemaLength = -1;
		int tableLength = -1;
		String schema = null;
		String table = null;
		byte[] schemaData = null;
		byte[] tableData = null;
		ArrayList<String> indexes;
		ArrayList<ArrayList<String>> keys;
		ArrayList<ArrayList<String>> types;
		ArrayList<ArrayList<Boolean>> orders;
		Vector<RIDAndIndexKeys> raiks = null;
		try
		{
			ObjectInputStream objIn = new ObjectInputStream(sock.getInputStream());
			readNonCoord(txBytes);
			txNum = bytesToLong(txBytes);
			readNonCoord(schemaLenBytes);
			schemaLength = bytesToInt(schemaLenBytes);
			schemaData = new byte[schemaLength];
			readNonCoord(schemaData);
			schema = new String(schemaData, "UTF-8");
			readNonCoord(tableLenBytes);
			tableLength = bytesToInt(tableLenBytes);
			tableData = new byte[tableLength];
			readNonCoord(tableData);
			table = new String(tableData, "UTF-8");
			indexes = (ArrayList<String>)objIn.readObject();
			raiks = (Vector<RIDAndIndexKeys>)objIn.readObject();
			keys = (ArrayList<ArrayList<String>>)objIn.readObject();
			types = (ArrayList<ArrayList<String>>)objIn.readObject();
			orders = (ArrayList<ArrayList<Boolean>>)objIn.readObject();
		}
		catch(Exception e)
		{
			sendNo();
			return;
		}
		
		MultiHashMap<Integer, RIDAndIndexKeys> map = new MultiHashMap<Integer, RIDAndIndexKeys>();
		for (RIDAndIndexKeys raik : raiks)
		{
			map.multiPut(raik.getRID().getDevice(), raik);
		}
		
		ArrayList<FlushDeleteThread> threads = new ArrayList<FlushDeleteThread>();
		for (Object o : map.getKeySet())
		{
			int device = (Integer)o;
			threads.add(new FlushDeleteThread(map.get(device), tx, schema, table, keys, types, orders, indexes));
		}
		
		for (FlushDeleteThread thread : threads)
		{
			thread.start();
		}
		
		boolean allOK = true;
		for (FlushDeleteThread thread : threads)
		{
			while (true)
			{
				try
				{
					thread.join();
					break;
				}
				catch(InterruptedException e)
				{}
			}
			if (!thread.getOK())
			{
				allOK = false;
			}
		}
		
		if (allOK)
		{
			sendOK();
			return;
		}
		else
		{
			sendNo();
			return;
		}
	}
	
	private class FlushDeleteThread extends HRDBMSThread
	{
		private Transaction tx;
		private boolean ok = true;
		String schema;
		String table;
		ArrayList<ArrayList<String>> keys;
		ArrayList<ArrayList<String>> types;
		ArrayList<ArrayList<Boolean>> orders;
		ArrayList<String> indexes;
		Vector<RIDAndIndexKeys> raiks;
		
		public FlushDeleteThread(Vector<RIDAndIndexKeys> raiks, Transaction tx, String schema, String table, ArrayList<ArrayList<String>> keys, ArrayList<ArrayList<String>> types, ArrayList<ArrayList<Boolean>> orders, ArrayList<String> indexes)
		{
			this.raiks = raiks;
			this.tx = tx;
			this.schema = schema;
			this.table = table;
			this.keys = keys;
			this.types = types;
			this.orders = orders;
			this.indexes = indexes;
		}
		
		public boolean getOK()
		{
			return ok;
		}
		
		public void run()
		{
			try
			{
				//delete row based on rid
				MultiHashMap<Integer, RIDAndIndexKeys> map = new MultiHashMap<Integer, RIDAndIndexKeys>();
				for (RIDAndIndexKeys raik : raiks)
				{
					map.multiPut(raik.getRID().getBlockNum(), raik);
				}
			
				int num = raiks.get(0).getRID().getDevice();
				HashMap<Integer, DataType> layout = new HashMap<Integer, DataType>();
				Schema sch = new Schema(layout);
				for (Object o : map.getKeySet())
				{
					int block = (Integer)o;
					//request block
					//delete every rid
					Block toRequest = new Block(new MetaData().getDevicePath(num) + schema + "." + table + ".tbl", block);
					tx.requestPage(toRequest);
					tx.read(toRequest, sch);
					for (Object o2 : map.get(block))
					{
						RIDAndIndexKeys raik = (RIDAndIndexKeys)o2;
						sch.deleteRow(raik.getRID());
					}
				
					//for each index, delete row based on rid and key values
					int i = 0;
					for (String index : indexes)
					{
						Index idx = new Index(new MetaData().getDevicePath(num) + index, keys.get(i), types.get(i), orders.get(i));
						idx.open();
						for (Object o2 : map.get(block))
						{
							RIDAndIndexKeys raik = (RIDAndIndexKeys)o2;
							idx.delete(aloToFieldValues(raik.getIndexKeys().get(i)),raik.getRID());
						}
						i++;
					}
				}
			}
			catch(Exception e)
			{
				ok = false;
			}
		}
	}
	
	private FieldValue[] aloToFieldValues(ArrayList<Object> row)
	{
		FieldValue[] retval = new FieldValue[row.size()];
		int i = 0;
		for (Object o : row)
		{
			if (o instanceof Integer)
			{
				retval[i] = new Schema.IntegerFV((Integer)o);
			}
			else if (o instanceof Long)
			{
				retval[i] = new Schema.BigintFV((Long)o);
			}
			else if (o instanceof Double)
			{
				retval[i] = new Schema.DoubleFV((Double)o);
			}
			else if (o instanceof MyDate)
			{
				retval[i] = new Schema.DateFV((MyDate)o);
			}
			else if (o instanceof String)
			{
				retval[i] = new Schema.VarcharFV((String)o);
			}
			
			i++;
		}
		
		return retval;
	}
	
	private void insert()
	{
		byte[] txBytes = new byte[8];
		long txNum = -1;
		byte[] schemaLenBytes = new byte[4];
		byte[] tableLenBytes = new byte[4];
		int schemaLength = -1;
		int tableLength = -1;
		String schema = null;
		String table = null;
		byte[] schemaData = null;
		byte[] tableData = null;
		ArrayList<String> indexes;
		ArrayList<ArrayList<String>> keys;
		ArrayList<ArrayList<String>> types;
		ArrayList<ArrayList<Boolean>> orders;
		Vector<ArrayList<Object>> list = null;
		HashMap<String, Integer> cols2Pos;
		PartitionMetaData partMeta;
		try
		{
			ObjectInputStream objIn = new ObjectInputStream(sock.getInputStream());
			readNonCoord(txBytes);
			txNum = bytesToLong(txBytes);
			readNonCoord(schemaLenBytes);
			schemaLength = bytesToInt(schemaLenBytes);
			schemaData = new byte[schemaLength];
			readNonCoord(schemaData);
			schema = new String(schemaData, "UTF-8");
			readNonCoord(tableLenBytes);
			tableLength = bytesToInt(tableLenBytes);
			tableData = new byte[tableLength];
			readNonCoord(tableData);
			table = new String(tableData, "UTF-8");
			indexes = (ArrayList<String>)objIn.readObject();
			list = (Vector<ArrayList<Object>>)objIn.readObject();
			keys = (ArrayList<ArrayList<String>>)objIn.readObject();
			types = (ArrayList<ArrayList<String>>)objIn.readObject();
			orders = (ArrayList<ArrayList<Boolean>>)objIn.readObject();
			cols2Pos = (HashMap<String, Integer>)objIn.readObject();
			partMeta = (PartitionMetaData)objIn.readObject();
		}
		catch(Exception e)
		{
			sendNo();
			return;
		}
		
		MultiHashMap<Integer, ArrayList<Object>> map = new MultiHashMap<Integer, ArrayList<Object>>();
		for (ArrayList<Object> row : list)
		{
			map.multiPut(MetaData.determineDevice(row, partMeta), row);
		}
		
		ArrayList<FlushInsertThread> threads = new ArrayList<FlushInsertThread>();
		for (Object o : map.getKeySet())
		{
			int device = (Integer)o;
			threads.add(new FlushInsertThread(map.get(device), tx, schema, table, keys, types, orders, indexes, cols2Pos, device));
		}
		
		for (FlushInsertThread thread : threads)
		{
			thread.start();
		}
		
		boolean allOK = true;
		for (FlushInsertThread thread : threads)
		{
			while (true)
			{
				try
				{
					thread.join();
					break;
				}
				catch(InterruptedException e)
				{}
			}
			if (!thread.getOK())
			{
				allOK = false;
			}
		}
		
		if (allOK)
		{
			sendOK();
			return;
		}
		else
		{
			sendNo();
			return;
		}
	}
	
	private class FlushInsertThread extends HRDBMSThread
	{
		private Transaction tx;
		private boolean ok = true;
		String schema;
		String table;
		ArrayList<ArrayList<String>> keys;
		ArrayList<ArrayList<String>> types;
		ArrayList<ArrayList<Boolean>> orders;
		ArrayList<String> indexes;
		Vector<ArrayList<Object>> list;
		HashMap<String, Integer> cols2Pos;
		int num;
		
		public FlushInsertThread(Vector<ArrayList<Object>> list, Transaction tx, String schema, String table, ArrayList<ArrayList<String>> keys, ArrayList<ArrayList<String>> types, ArrayList<ArrayList<Boolean>> orders, ArrayList<String> indexes, HashMap<String, Integer> cols2Pos, int num)
		{
			this.list = list;
			this.tx = tx;
			this.schema = schema;
			this.table = table;
			this.keys = keys;
			this.types = types;
			this.orders = orders;
			this.indexes = indexes;
			this.cols2Pos = cols2Pos;
			this.num = num;
		}
		
		public boolean getOK()
		{
			return ok;
		}
		
		public void run()
		{
			try
			{
				//insert row and create RAIKS
				int block = Schema.HEADER_SIZE;
				//request block
				HashMap<Integer, DataType> layout = new HashMap<Integer, DataType>();
				Schema sch = new Schema(layout);
				Block toRequest = new Block(new MetaData().getDevicePath(num) + schema + "." + table + ".tbl", block);
				tx.requestPage(toRequest);
				tx.read(toRequest, sch);
				ArrayList<RIDAndIndexKeys> raiks = new ArrayList<RIDAndIndexKeys>();
				for (ArrayList<Object> row : list)
				{
					RID rid = sch.insertRow(aloToFieldValues(row));
					ArrayList<ArrayList<Object>> indexKeys = new ArrayList<ArrayList<Object>>();
					int i = 0;
					for (String index : indexes)
					{
						ArrayList<String> key = keys.get(i);
						ArrayList<Object> k = new ArrayList<Object>();
						for (String col : key)
						{
							k.add(row.get(cols2Pos.get(col)));
						}
						
						indexKeys.add(k);
					}
					
					raiks.add(new RIDAndIndexKeys(rid, indexKeys));
				}
				
				//for each index, insert row based on rid and key values
				int i = 0;
				for (String index : indexes)
				{
					Index idx = new Index(new MetaData().getDevicePath(num) + index, keys.get(i), types.get(i), orders.get(i));
					idx.open();
					for (RIDAndIndexKeys raik : raiks)
					{
						idx.insert(aloToFieldValues(raik.getIndexKeys().get(i)), raik.getRID());
					}
					i++;
				}
			}
			catch(Exception e)
			{
				ok = false;
			}
		}
	}
	
	private void update()
	{
		byte[] txBytes = new byte[8];
		long txNum = -1;
		byte[] schemaLenBytes = new byte[4];
		byte[] tableLenBytes = new byte[4];
		int schemaLength = -1;
		int tableLength = -1;
		String schema = null;
		String table = null;
		byte[] schemaData = null;
		byte[] tableData = null;
		ArrayList<String> indexes;
		ArrayList<ArrayList<String>> keys;
		ArrayList<ArrayList<String>> types;
		ArrayList<ArrayList<Boolean>> orders;
		Vector<UpdateSetAndRAIK> list = null;
		HashMap<String, Integer> cols2Pos;
		ArrayList<String> cols;
		try
		{
			ObjectInputStream objIn = new ObjectInputStream(sock.getInputStream());
			readNonCoord(txBytes);
			txNum = bytesToLong(txBytes);
			readNonCoord(schemaLenBytes);
			schemaLength = bytesToInt(schemaLenBytes);
			schemaData = new byte[schemaLength];
			readNonCoord(schemaData);
			schema = new String(schemaData, "UTF-8");
			readNonCoord(tableLenBytes);
			tableLength = bytesToInt(tableLenBytes);
			tableData = new byte[tableLength];
			readNonCoord(tableData);
			table = new String(tableData, "UTF-8");
			indexes = (ArrayList<String>)objIn.readObject();
			list = (Vector<UpdateSetAndRAIK>)objIn.readObject();
			keys = (ArrayList<ArrayList<String>>)objIn.readObject();
			types = (ArrayList<ArrayList<String>>)objIn.readObject();
			orders = (ArrayList<ArrayList<Boolean>>)objIn.readObject();
			cols2Pos = (HashMap<String, Integer>)objIn.readObject();
			cols = (ArrayList<String>)objIn.readObject();
		}
		catch(Exception e)
		{
			sendNo();
			return;
		}
		
		MultiHashMap<Integer, UpdateSetAndRAIK> map = new MultiHashMap<Integer, UpdateSetAndRAIK>();
		for (UpdateSetAndRAIK usraik : list)
		{
			map.multiPut(usraik.getRAIK().getRID().getDevice(), usraik);
		}
		
		ArrayList<FlushUpdateThread> threads = new ArrayList<FlushUpdateThread>();
		for (Object o : map.getKeySet())
		{
			int device = (Integer)o;
			threads.add(new FlushUpdateThread(map.get(device), tx, schema, table, keys, types, orders, indexes, cols2Pos, cols));
		}
		
		for (FlushUpdateThread thread : threads)
		{
			thread.start();
		}
		
		boolean allOK = true;
		for (FlushUpdateThread thread : threads)
		{
			while (true)
			{
				try
				{
					thread.join();
					break;
				}
				catch(InterruptedException e)
				{}
			}
			if (!thread.getOK())
			{
				allOK = false;
			}
		}
		
		if (allOK)
		{
			sendOK();
			return;
		}
		else
		{
			sendNo();
			return;
		}
	}
	
	private class FlushUpdateThread extends HRDBMSThread
	{
		private Transaction tx;
		private boolean ok = true;
		String schema;
		String table;
		ArrayList<ArrayList<String>> keys;
		ArrayList<ArrayList<String>> types;
		ArrayList<ArrayList<Boolean>> orders;
		ArrayList<String> indexes;
		Vector<UpdateSetAndRAIK> list;
		HashMap<String, Integer> cols2Pos;
		ArrayList<String> cols;
		
		public FlushUpdateThread(Vector<UpdateSetAndRAIK> list, Transaction tx, String schema, String table, ArrayList<ArrayList<String>> keys, ArrayList<ArrayList<String>> types, ArrayList<ArrayList<Boolean>> orders, ArrayList<String> indexes, HashMap<String, Integer> cols2Pos, ArrayList<String> cols)
		{
			this.list = list;
			this.tx = tx;
			this.schema = schema;
			this.table = table;
			this.keys = keys;
			this.types = types;
			this.orders = orders;
			this.indexes = indexes;
			this.cols2Pos = cols2Pos;
			this.cols = cols;
		}
		
		public boolean getOK()
		{
			return ok;
		}
		
		public void run()
		{
			try
			{
				MultiHashMap<Integer, UpdateSetAndRAIK> map = new MultiHashMap<Integer, UpdateSetAndRAIK>();
				for (UpdateSetAndRAIK usraik : list)
				{
					map.multiPut(usraik.getRAIK().getRID().getBlockNum(), usraik);
				}
			
				int num = list.get(0).getRAIK().getRID().getDevice();
				HashMap<Integer, DataType> layout = new HashMap<Integer, DataType>();
				Schema sch = new Schema(layout);
				ArrayList<IndexUpdate> indexUpdates = new ArrayList<IndexUpdate>();
				
				for (Object o : map.getKeySet())
				{
					int block = (Integer)o;
					Block toRequest = new Block(new MetaData().getDevicePath(num) + schema + "." + table + ".tbl", block);
					tx.requestPage(toRequest);
					tx.read(toRequest, sch);
					for (Object o2 : map.get(block))
					{
						UpdateSetAndRAIK usraik = (UpdateSetAndRAIK)o2;
						Row row = sch.getRow(usraik.getRAIK().getRID());
						FieldValue[] oldFV = row.getAllCols();
						FieldValue[] newFV = row.getAllCols();
						FieldValue[] tempFV = aloToFieldValues(usraik.getUpdateSet());
						int i = 0;
						for (String col : cols)
						{
							newFV[cols2Pos.get(col)] = tempFV[i];
							i++;
						}
						
						RIDChange ridChange = sch.updateRow(usraik.getRAIK().getRID(), 0, newFV);
						indexUpdates.add(new IndexUpdate(oldFV, newFV, ridChange.getOld(), ridChange.getNew()));
					}
				
					int i = 0;
					for (String index : indexes)
					{
						Index idx = new Index(new MetaData().getDevicePath(num) + index, keys.get(i), types.get(i), orders.get(i));
						idx.open();
						ArrayList<String> k = keys.get(i);
						boolean contains = false;
						for (String col : cols)
						{
							if (k.contains(col))
							{
								contains = true;
								break;
							}
						}
						for (IndexUpdate idxu : indexUpdates)
						{
							FieldValue[] oldKeyFV = new FieldValue[k.size()];
							FieldValue[] newKeyFV = new FieldValue[k.size()];
							int j = 0;
							for (String col : keys.get(i))
							{
								oldKeyFV[j] = idxu.oldFV[cols2Pos.get(col)];
								newKeyFV[j] = idxu.newFV[cols2Pos.get(col)];
								j++;
							}
							
							if (!contains)
							{
								idx.update(newKeyFV, idxu.oldRID, idxu.newRID);
							}
							else
							{
								idx.delete(oldKeyFV, idxu.oldRID);
								idx.insert(newKeyFV, idxu.newRID);
							}
						}
						i++;
					}
				}
			}
			catch(Exception e)
			{
				ok = false;
			}
		}
	}
	
	private static class IndexUpdate
	{
		public FieldValue[] oldFV;
		public FieldValue[] newFV;
		public RID oldRID;
		public RID newRID;
		
		public IndexUpdate(FieldValue[] oldFV, FieldValue[] newFV, RID oldRID, RID newRID)
		{
			this.oldFV = oldFV;
			this.newFV = newFV;
			this.oldRID = oldRID;
			this.newRID = newRID;
		}
	}
	
	private void executeUpdate()
	{
		byte[] bLen = new byte[4];
		read(bLen);
		byte[] bStmt = new byte[bytesToInt(bLen)];
		read(bStmt);
		String sql = null;
		try
		{
			sql = new String(bStmt, "UTF-8");
		}
		catch(Exception e)
		{}
		
		if (tx == null)
		{
			tx = new Transaction(Transaction.ISOLATION_CS);
		}
		
		try
		{
			worker = XAManager.executeUpdate(sql, tx, this);
			HRDBMSWorker.addThread(worker);
			worker.join();
			int updateCount = worker.getUpdateCount();
			if (updateCount == -1)
			{
				sendNo();
				returnExceptionToClient(worker.getException());
				return;
			}
			this.sendOK();
			sendInt(updateCount);
		}
		catch(Exception e)
		{
			this.sendNo();
			returnExceptionToClient(e);
		}
	}
}