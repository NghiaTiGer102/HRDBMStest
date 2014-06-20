package com.exascale.managers;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import com.exascale.filesystem.Block;
import com.exascale.logging.CommitLogRec;
import com.exascale.logging.DeleteLogRec;
import com.exascale.logging.ForwardLogIterator;
import com.exascale.logging.InsertLogRec;
import com.exascale.logging.LogIterator;
import com.exascale.logging.LogRec;
import com.exascale.logging.NQCheckLogRec;
import com.exascale.logging.NotReadyLogRec;
import com.exascale.logging.PrepareLogRec;
import com.exascale.logging.ReadyLogRec;
import com.exascale.logging.RollbackLogRec;
import com.exascale.logging.XAAbortLogRec;
import com.exascale.logging.XACommitLogRec;
import com.exascale.threads.ArchiverThread;
import com.exascale.threads.HRDBMSThread;

public class LogManager extends HRDBMSThread
{
	private static Long last_lsn;
	public static Map<String, FileChannel> openFiles = new HashMap<String, FileChannel>();
	private static String filename;
	public static ConcurrentHashMap<String, LinkedList<LogRec>> logs = new ConcurrentHashMap<String, LinkedList<LogRec>>();
	private static BlockingQueue<String> in = new LinkedBlockingQueue<String>();
	public static Boolean noArchive = false;
	public static int openIters = 0;

	static
	{
		last_lsn = System.currentTimeMillis() * 1000000;
	}

	public LogManager()
	{
		HRDBMSWorker.logger.info("Starting initialization of the Log Manager.");
		this.setWait(true);
		this.description = "Log Manager";
	}

	public static void commit(long txnum) throws IOException
	{
		commit(txnum, filename);
	}

	public static void commit(long txnum, String fn) throws IOException
	{
		final LogRec rec = new CommitLogRec(txnum);
		write(rec, fn);
		flush(rec.lsn(), fn);
	}
	
	public static void ready(long txnum, String host) throws IOException
	{
		ready(txnum, host, filename);
	}

	public static void ready(long txnum, String host, String fn) throws IOException
	{
		final LogRec rec = new ReadyLogRec(txnum, host);
		write(rec, fn);
		flush(rec.lsn(), fn);
	}
	
	public static void notReady(long txnum) throws IOException
	{
		notReady(txnum, filename);
	}

	public static void notReady(long txnum, String fn) throws IOException
	{
		final LogRec rec = new NotReadyLogRec(txnum);
		write(rec, fn);
		flush(rec.lsn(), fn);
	}

	public static DeleteLogRec delete(long txnum, Block b, int off, byte[] before, byte[] after)
	{
		return delete(txnum, b, off, before, after, filename);
	}

	public static DeleteLogRec delete(long txnum, Block b, int off, byte[] before, byte[] after, String fn)
	{
		final DeleteLogRec rec = new DeleteLogRec(txnum, b, off, before, after);
		write(rec, fn);
		return rec;
	}

	public static void flush(long lsn) throws IOException
	{
		flush(lsn, filename);
	}

	public static void flush(long lsn, String fn) throws IOException
	{
		final LinkedList<LogRec> list = logs.get(fn);
		synchronized (noArchive)
		{
			synchronized (list)
			{
				final FileChannel fc = getFile(fn);
				for (final LogRec rec : list)
				{
					if (rec.lsn() <= lsn)
					{
						try
						{
							synchronized (fc)
							{
								fc.position(fc.size());
								final ByteBuffer size = ByteBuffer.allocate(4);
								size.putInt(rec.size());
								size.position(0);
								rec.buffer().position(0);
								fc.write(size);
								fc.write(rec.buffer());
								size.position(0);
								fc.write(size);
							}
							logs.get(fn).remove(rec);

							if (fc.size() > Long.parseLong(HRDBMSWorker.getHParms().getProperty("target_log_size")))
							{
								if (!noArchive)
								{
									runArchive(fn);
								}
							}
						}
						catch (final IOException e)
						{
							throw (e);
						}
					}
					else
					{
						break;
					}
				}
			}
		}
	}

	public static void flushAll() throws IOException
	{
		final LinkedList<LogRec> list = logs.get(filename);
		if (list == null)
		{
			return;
		}
		synchronized (list)
		{
			if (list.size() != 0)
			{
				flush(list.getLast().lsn());
			}
		}
	}

	public static Iterator<LogRec> forwardIterator()
	{
		return forwardIterator(filename);
	}

	public static Iterator<LogRec> forwardIterator(String fn)
	{
		try
		{
			return new ForwardLogIterator(fn);
		}
		catch (final Exception e)
		{
			HRDBMSWorker.logger.error("Error creating ForwardLogIterator.", e);
			return null;
		}
	}

	public static synchronized FileChannel getFile(String filename) throws IOException
	{
		synchronized (noArchive)
		{
			FileChannel fc = openFiles.get(filename);

			if (fc == null)
			{
				final File table = new File(filename);
				final RandomAccessFile f = new RandomAccessFile(table, "rws");
				fc = f.getChannel();
				openFiles.put(filename, fc);
				logs.put(filename, new LinkedList<LogRec>());
			}

			return fc;
		}
	}

	public static BlockingQueue<String> getInputQueue()
	{
		return in;
	}

	public static long getLSN()
	{
		synchronized (last_lsn)
		{
			final long new_lsn = System.currentTimeMillis() * 1000000;
			if (new_lsn > last_lsn.longValue())
			{
				last_lsn = new_lsn;
				return new_lsn;
			}
			else
			{
				last_lsn++;
				return last_lsn;
			}
		}
	}

	public static InsertLogRec insert(long txnum, Block b, int off, byte[] before, byte[] after)
	{
		return insert(txnum, b, off, before, after, filename);
	}

	public static InsertLogRec insert(long txnum, Block b, int off, byte[] before, byte[] after, String fn)
	{
		final InsertLogRec rec = new InsertLogRec(txnum, b, off, before, after);
		write(rec, fn);
		return rec;
	}

	public static Iterator<LogRec> iterator()
	{
		return iterator(filename);
	}

	public static Iterator<LogRec> iterator(String fn)
	{
		try
		{
			return new LogIterator(fn);
		}
		catch (final Exception e)
		{
			HRDBMSWorker.logger.error("Error creating LogIterator.", e);
			return null;
		}
	}

	public static void rollback(long txnum) throws IOException
	{
		rollback(txnum, filename);
	}

	public static void rollback(long txnum, String fn) throws IOException
	{
		final Iterator<LogRec> iter = iterator(fn);
		while (iter.hasNext())
		{
			final LogRec rec = iter.next();
			if (rec.txnum() == txnum)
			{
				if (rec.type() == LogRec.START)
				{
					return;
				}

				rec.undo();
			}
		}

		((LogIterator)iter).close();
		final LogRec rec = new RollbackLogRec(txnum);
		write(rec, fn);
		flush(rec.lsn(), fn);
	}

	public static long write(LogRec rec)
	{
		return write(rec, filename);
	}

	public static long write(LogRec rec, String fn)
	{
		rec.setTimeStamp(System.currentTimeMillis());
		final LinkedList<LogRec> list = logs.get(fn);
		long retval;
		retval = getLSN();
		rec.setLSN(retval);
		synchronized (list)
		{
			list.add(rec);
		}
		return retval;
	}

	private static void runArchive(String fn)
	{
		HRDBMSWorker.addThread(new ArchiverThread(fn));
	}

	public void recover() throws IOException
	{
		recover(filename);
	}

	public void recover(String fn) throws IOException
	{
		final LinkedList<LogRec> list = logs.get(fn);
		synchronized (list)
		{
			final Iterator<LogRec> iter = iterator(fn);
			final HashSet<Long> commitList = new HashSet<Long>();
			final HashSet<Long> rollbackList = new HashSet<Long>();
			final HashSet<Long> needsCommit = new HashSet<Long>();
			final HashSet<Long> commitListXA = new HashSet<Long>();
			final HashSet<Long> rollbackListXA = new HashSet<Long>();
			while (iter.hasNext())
			{
				final LogRec rec = iter.next();
				if (rec.type() == LogRec.COMMIT)
				{
					commitList.add(rec.txnum());
				}
				else if (rec.type() == LogRec.ROLLB || rec.type() == LogRec.NOTREADY)
				{
					rollbackList.add(rec.txnum());
				}
				else if (rec.type() == LogRec.READY)
				{
					ReadyLogRec xa = (ReadyLogRec)rec.rebuild();
					if (XAManager.askXAManager(xa))
					{
						commitList.add(rec.txnum());
						needsCommit.add(rec.txnum());
					}
					else
					{
						rollbackList.add(rec.txnum());
					}
				}
				else if (rec.type() == LogRec.XACOMMIT)
				{
					XACommitLogRec xa = (XACommitLogRec)rec.rebuild();
					XAManager.phase2(xa.txnum(), xa.getNodes());
					commitListXA.add(rec.txnum());
				}
				else if (rec.type() == LogRec.XAABORT)
				{
					XAAbortLogRec xa = (XAAbortLogRec)rec.rebuild();
					XAManager.rollback(xa.txnum(), xa.getNodes());
					rollbackListXA.add(rec.txnum());
				}
				else if (rec.type() == LogRec.PREPARE)
				{
					if (rollbackListXA.contains(rec.txnum()) || commitListXA.contains(rec.txnum()))
					{
					}
					else
					{
						PrepareLogRec xa = (PrepareLogRec)rec.rebuild();
						XAManager.rollback(xa.txnum(), xa.getNodes());
						rollbackListXA.add(rec.txnum());
					}
				}
				else if (rec.type() == LogRec.INSERT || rec.type() == LogRec.DELETE)
				{
					if ((!commitList.contains(rec.txnum())) && (!rollbackList.contains(rec.txnum())))
					{
						rec.rebuild().undo();
					}
				}
			}

			final Iterator<LogRec> iter2 = forwardIterator(fn);
			((LogIterator)iter).close();

			while (iter2.hasNext())
			{
				final LogRec rec = iter2.next();
				if (rec.type() == LogRec.INSERT || rec.type() == LogRec.DELETE)
				{
					if (commitList.contains(rec.txnum()))
					{
						rec.rebuild().redo();
					}
				}
			}

			((ForwardLogIterator)iter2).close();
			for (long txnum : needsCommit)
			{
				this.commit(txnum);
			}
			final LogRec rec = new NQCheckLogRec(new HashSet<Long>());
			write(rec, fn);
			flush(rec.lsn(), fn);
		}
	}

	@Override
	public void run()
	{
		filename = HRDBMSWorker.getHParms().getProperty("log_dir");
		if (!filename.endsWith("/"))
		{
			filename += "/";
		}
		String filename2 = new String(filename);
		filename += "active.log";
		filename2 += "xa.log";
		File log = new File(filename);
		if (!log.exists())
		{
			try
			{
				log.createNewFile();
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("Error creating the active log file.", e);
				in = null;
				this.terminate();
				return;
			}
		}
		try
		{
			getFile(filename);
		}
		catch (final Exception e)
		{
			HRDBMSWorker.logger.error("Error getting a FileChannel for " + filename, e);
			in = null;
			this.terminate();
			return;
		}
		
		if (HRDBMSWorker.type == HRDBMSWorker.TYPE_COORD || HRDBMSWorker.type == HRDBMSWorker.TYPE_MASTER)
		{
			log = new File(filename2);
			if (!log.exists())
			{
				try
				{
					log.createNewFile();
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("Error creating the xa log file.", e);
					in = null;
					this.terminate();
					return;
				}
			}
			try
			{
				getFile(filename2);
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("Error getting a FileChannel for " + filename2, e);
				in = null;
				this.terminate();
				return;
			}
		}

		final int sleepSecs = Integer.parseInt(HRDBMSWorker.getHParms().getProperty("log_clean_sleep_secs"));
		try
		{
			recover(); // sync on everything possible to delay every synchronous
						// method call
			if (HRDBMSWorker.type == HRDBMSWorker.TYPE_COORD || HRDBMSWorker.type == HRDBMSWorker.TYPE_MASTER)
			{
				recover(filename2);
			}
		}
		catch (final IOException e)
		{
			HRDBMSWorker.logger.error("Error during log recovery.", e);
			in = null;
			this.terminate();
			return;
		}

		HRDBMSWorker.logger.info("Log Manager initialization complete.");
		while (true)
		{
			final String msg = in.poll();
			if (msg != null)
			{
				processMessage(msg);
			}

			boolean nothing = true;
			for (final Map.Entry<String, LinkedList<LogRec>> entry : logs.entrySet())
			{
				final LinkedList<LogRec> list = entry.getValue();
				final String fn = entry.getKey();
				if (!list.isEmpty())
				{
					nothing = false;
					try
					{
						flush(list.removeFirst().lsn(), fn);
					}
					catch (final IOException e)
					{
						in = null;
						HRDBMSWorker.logger.error("Error flushing log pages to disk.", e);
						this.terminate();
						return;
					}
				}
			}

			if (nothing)
			{
				try
				{
					Thread.sleep(sleepSecs * 1000);
				}
				catch (final InterruptedException e)
				{
				}
			}
		}
	}

	private void addLog(String cmd)
	{
		final String fn = cmd.substring(8);
		final File log = new File(fn);
		if (!log.exists())
		{
			try
			{
				log.createNewFile();
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("Error creating log file " + log, e);
				in = null;
				this.terminate();
				return;
			}
		}
		try
		{
			getFile(fn);
			recover(fn);
		}
		catch (final Exception e)
		{
			HRDBMSWorker.logger.error("Error recovery secondary log " + fn, e);
			in = null;
			this.terminate();
			return;
		}
	}

	private void processMessage(String cmd)
	{
		if (cmd.startsWith("ADD LOG"))
		{
			addLog(cmd);
		}
		else
		{
			HRDBMSWorker.logger.error("Unknown message received by Log Manager: " + cmd);
			in = null;
			this.terminate();
			return;
		}
	}
}