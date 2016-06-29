package com.exascale.misc;

import java.util.concurrent.locks.LockSupport;

public class SPSCQueue
{
	private final Object[] q;
	volatile long head = -1;
	volatile long tail = 0;
	final int length;

	public SPSCQueue(int requestedSize)
	{
		q = new Object[Integer.highestOneBit(requestedSize)];
		length = q.length;
	}

	public void clear()
	{
		head = -1;
		tail = 0;
		int i = 0;
		while (i < length)
		{
			q[i] = null;
			i++;
		}
	}

	public Object peek()
	{
		if (head + 1 == tail)
		{
			return null;
		}

		return q[(int)((head + 1) & (length - 1))];
	}

	public void put(Object o)
	{
		while (tail - head > length)
		{
			LockSupport.parkNanos(500);
		}

		q[(int)(tail & (length - 1))] = o;
		tail++;
	}

	public Object take() throws InterruptedException
	{
		while (head + 1 == tail)
		{
			LockSupport.parkNanos(500);
		}
		final int index = (int)((head + 1) & (length - 1));
		Object retval = q[index];
		q[index] = null;
		head++;
		return retval;
	}
}
