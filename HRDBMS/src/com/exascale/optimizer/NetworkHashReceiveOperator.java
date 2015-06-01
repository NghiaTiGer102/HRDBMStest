package com.exascale.optimizer;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.IdentityHashMap;
import com.exascale.managers.HRDBMSWorker;
import com.exascale.misc.DataEndMarker;

public final class NetworkHashReceiveOperator extends NetworkReceiveOperator
{
	private static sun.misc.Unsafe unsafe;

	static
	{
		try
		{
			final Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			unsafe = (sun.misc.Unsafe)f.get(null);
		}
		catch (Exception e)
		{
			unsafe = null;
		}
	}

	private int ID;

	public NetworkHashReceiveOperator(int ID, MetaData meta)
	{
		this.ID = ID;
		this.meta = meta;
	}

	public static NetworkHashReceiveOperator deserialize(InputStream in, HashMap<Long, Object> prev) throws Exception
	{
		NetworkHashReceiveOperator value = (NetworkHashReceiveOperator)unsafe.allocateInstance(NetworkHashReceiveOperator.class);
		prev.put(OperatorUtils.readLong(in), value);
		value.meta = new MetaData();
		value.children = OperatorUtils.deserializeALOp(in, prev);
		value.parent = OperatorUtils.deserializeOperator(in, prev);
		value.cols2Types = OperatorUtils.deserializeStringHM(in, prev);
		value.cols2Pos = OperatorUtils.deserializeStringIntHM(in, prev);
		value.pos2Col = OperatorUtils.deserializeTM(in, prev);
		value.fullyStarted = OperatorUtils.readBool(in);
		value.start = OperatorUtils.readLong(in);
		value.node = OperatorUtils.readInt(in);
		value.ID = OperatorUtils.readInt(in);
		value.cd = cs.newDecoder();
		return value;
	}

	@Override
	public NetworkHashReceiveOperator clone()
	{
		final NetworkHashReceiveOperator retval = new NetworkHashReceiveOperator(ID, meta);
		retval.node = node;
		return retval;
	}

	public int getID()
	{
		return ID;
	}

	@Override
	public Object next(Operator op2) throws Exception
	{
		if (!fullyStarted)
		{
			synchronized (this)
			{
				if (!fullyStarted)
				{
					HRDBMSWorker.logger.debug("Starting NetworkHashReceiveOperator(" + node + ") ID = " + ID);
					fullyStarted = true;
					for (final Operator op : children)
					{
						final NetworkSendOperator child = (NetworkSendOperator)op;
						child.clearParent();
						Socket sock = null;
						try
						{
							// sock = new
							// Socket(meta.getHostNameForNode(child.getNode()),
							// WORKER_PORT);
							sock = new Socket();
							sock.setReceiveBufferSize(262144);
							sock.setSendBufferSize(262144);
							sock.connect(new InetSocketAddress(meta.getHostNameForNode(child.getNode()), WORKER_PORT));
						}
						catch (final java.net.ConnectException e)
						{
							HRDBMSWorker.logger.error("Connection failed to " + meta.getHostNameForNode(child.getNode()), e);
							throw e;
						}
						socks.put(child, sock);
						final OutputStream out = sock.getOutputStream();
						outs.put(child, out);
						final InputStream in = new BufferedInputStream(sock.getInputStream(), 65536);
						ins.put(child, in);
						final byte[] command = "SNDRMTTR".getBytes(StandardCharsets.UTF_8);
						final byte[] from = intToBytes(node);
						final byte[] to = intToBytes(child.getNode());
						final byte[] idBytes = intToBytes(ID);
						final byte[] data = new byte[command.length + from.length + to.length + idBytes.length];
						System.arraycopy(command, 0, data, 0, 8);
						System.arraycopy(from, 0, data, 8, 4);
						System.arraycopy(to, 0, data, 12, 4);
						System.arraycopy(idBytes, 0, data, 16, 4);
						out.write(data);
						out.flush();
						final int doSend = in.read();
						if (doSend != 0)
						{
							IdentityHashMap<Object, Long> map = new IdentityHashMap<Object, Long>();
							child.serialize(out, map);
							map.clear();
							map = null;
							out.flush();
						}
					}

					new InitThread().start();
				}
			}
		}
		Object o;
		o = outBuffer.take();

		if (o instanceof DataEndMarker)
		{
			o = outBuffer.peek();
			if (o == null)
			{
				outBuffer.put(new DataEndMarker());
				return new DataEndMarker();
			}
			else
			{
				outBuffer.put(new DataEndMarker());
				return o;
			}
		}

		if (o instanceof Exception)
		{
			throw (Exception)o;
		}
		return o;
	}

	@Override
	public void serialize(OutputStream out, IdentityHashMap<Object, Long> prev) throws Exception
	{
		Long id = prev.get(this);
		if (id != null)
		{
			OperatorUtils.serializeReference(id, out);
			return;
		}

		OperatorUtils.writeType(74, out);
		prev.put(this, OperatorUtils.writeID(out));
		// recreate meta
		OperatorUtils.serializeALOp(children, out, prev);
		parent.serialize(out, prev);
		OperatorUtils.serializeStringHM(cols2Types, out, prev);
		OperatorUtils.serializeStringIntHM(cols2Pos, out, prev);
		OperatorUtils.serializeTM(pos2Col, out, prev);
		OperatorUtils.writeBool(fullyStarted, out);
		OperatorUtils.writeLong(start, out);
		OperatorUtils.writeInt(node, out);
		OperatorUtils.writeInt(ID, out);
	}

	public void setID(int ID)
	{
		this.ID = ID;
	}

	@Override
	public String toString()
	{
		return "NetworkHashReceiveOperator(" + node + ") ID = " + ID;
	}
}
