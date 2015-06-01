package com.exascale.misc;

import java.io.ObjectStreamField;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * A hash table supporting full concurrency of retrievals and high expected
 * concurrency for updates. This class obeys the same functional specification
 * as {@link java.util.Hashtable}, and includes versions of methods
 * corresponding to each method of {@code Hashtable}. However, even though all
 * operations are thread-safe, retrieval operations do <em>not</em> entail
 * locking, and there is <em>not</em> any support for locking the entire table
 * in a way that prevents all access. This class is fully interoperable with
 * {@code Hashtable} in programs that rely on its thread safety but not on its
 * synchronization details.
 *
 * <p>
 * Retrieval operations (including {@code get}) generally do not block, so may
 * overlap with update operations (including {@code put} and {@code remove}).
 * Retrievals reflect the results of the most recently <em>completed</em> update
 * operations holding upon their onset. (More formally, an update operation for
 * a given key bears a <em>happens-before</em> relation with any (non-null)
 * retrieval for that key reporting the updated value.) For aggregate operations
 * such as {@code putAll} and {@code clear}, concurrent retrievals may reflect
 * insertion or removal of only some entries. Similarly, Iterators, Spliterators
 * and Enumerations return elements reflecting the state of the hash table at
 * some point at or since the creation of the iterator/enumeration. They do
 * <em>not</em> throw {@link java.util.ConcurrentModificationException
 * ConcurrentModificationException}. However, iterators are designed to be used
 * by only one thread at a time. Bear in mind that the results of aggregate
 * status methods including {@code size}, {@code isEmpty}, and
 * {@code containsValue} are typically useful only when a map is not undergoing
 * concurrent updates in other threads. Otherwise the results of these methods
 * reflect transient states that may be adequate for monitoring or estimation
 * purposes, but not for program control.
 *
 * <p>
 * The table is dynamically expanded when there are too many collisions (i.e.,
 * keys that have distinct hash codes but fall into the same slot modulo the
 * table size), with the expected average effect of maintaining roughly two bins
 * per mapping (corresponding to a 0.75 load factor threshold for resizing).
 * There may be much variance around this average as mappings are added and
 * removed, but overall, this maintains a commonly accepted time/space tradeoff
 * for hash tables. However, resizing this or any other kind of hash table may
 * be a relatively slow operation. When possible, it is a good idea to provide a
 * size estimate as an optional {@code initialCapacity} constructor argument. An
 * additional optional {@code loadFactor} constructor argument provides a
 * further means of customizing initial table capacity by specifying the table
 * density to be used in calculating the amount of space to allocate for the
 * given number of elements. Also, for compatibility with previous versions of
 * this class, constructors may optionally specify an expected
 * {@code concurrencyLevel} as an additional hint for internal sizing. Note that
 * using many keys with exactly the same {@code hashCode()} is a sure way to
 * slow down performance of any hash table. To ameliorate impact, when keys are
 * {@link Comparable}, this class may use comparison order among keys to help
 * break ties.
 *
 * <p>
 * A {@link Set} projection of a ConcurrentHashMap may be created (using
 * {@link #newKeySet()} or {@link #newKeySet(int)}), or viewed (using
 * {@link #keySet(Object)} when only keys are of interest, and the mapped values
 * are (perhaps transiently) not used or all take the same mapping value.
 *
 * <p>
 * A ConcurrentHashMap can be used as scalable frequency map (a form of
 * histogram or multiset) by using {@link java.util.concurrent.atomic.LongAdder}
 * values and initializing via {@link #computeIfAbsent computeIfAbsent}. For
 * example, to add a count to a
 * {@code ConcurrentHashMap<String,LongAdder> freqs}, you can use
 * {@code freqs.computeIfAbsent(k -> new LongAdder()).increment();}
 *
 * <p>
 * This class and its views and iterators implement all of the <em>optional</em>
 * methods of the {@link Map} and {@link Iterator} interfaces.
 *
 * <p>
 * Like {@link Hashtable} but unlike {@link HashMap}, this class does
 * <em>not</em> allow {@code null} to be used as a key or value.
 *
 * <p>
 * ConcurrentHashMaps support a set of sequential and parallel bulk operations
 * that, unlike most {@link Stream} methods, are designed to be safely, and
 * often sensibly, applied even with maps that are being concurrently updated by
 * other threads; for example, when computing a snapshot summary of the values
 * in a shared registry. There are three kinds of operation, each with four
 * forms, accepting functions with Keys, Values, Entries, and (Key, Value)
 * arguments and/or return values. Because the elements of a ConcurrentHashMap
 * are not ordered in any particular way, and may be processed in different
 * orders in different parallel executions, the correctness of supplied
 * functions should not depend on any ordering, or on any other objects or
 * values that may transiently change while computation is in progress; and
 * except for forEach actions, should ideally be side-effect-free. Bulk
 * operations on {@link java.util.Map.Entry} objects do not support method
 * {@code setValue}.
 *
 * <ul>
 * <li>forEach: Perform a given action on each element. A variant form applies a
 * given transformation on each element before performing the action.</li>
 *
 * <li>search: Return the first available non-null result of applying a given
 * function on each element; skipping further search when a result is found.</li>
 *
 * <li>reduce: Accumulate each element. The supplied reduction function cannot
 * rely on ordering (more formally, it should be both associative and
 * commutative). There are five variants:
 *
 * <ul>
 *
 * <li>Plain reductions. (There is not a form of this method for (key, value)
 * function arguments since there is no corresponding return type.)</li>
 *
 * <li>Mapped reductions that accumulate the results of a given function applied
 * to each element.</li>
 *
 * <li>Reductions to scalar doubles, longs, and ints, using a given basis value.
 * </li>
 *
 * </ul>
 * </li>
 * </ul>
 *
 * <p>
 * These bulk operations accept a {@code parallelismThreshold} argument. Methods
 * proceed sequentially if the current map size is estimated to be less than the
 * given threshold. Using a value of {@code Long.MAX_VALUE} suppresses all
 * parallelism. Using a value of {@code 1} results in maximal parallelism by
 * partitioning into enough subtasks to fully utilize the
 * {@link ForkJoinPool#commonPool()} that is used for all parallel computations.
 * Normally, you would initially choose one of these extreme values, and then
 * measure performance of using in-between values that trade off overhead versus
 * throughput.
 *
 * <p>
 * The concurrency properties of bulk operations follow from those of
 * ConcurrentHashMap: Any non-null result returned from {@code get(key)} and
 * related access methods bears a happens-before relation with the associated
 * insertion or update. The result of any bulk operation reflects the
 * composition of these per-element relations (but is not necessarily atomic
 * with respect to the map as a whole unless it is somehow known to be
 * quiescent). Conversely, because keys and values in the map are never null,
 * null serves as a reliable atomic indicator of the current lack of any result.
 * To maintain this property, null serves as an implicit basis for all
 * non-scalar reduction operations. For the double, long, and int versions, the
 * basis should be one that, when combined with any other value, returns that
 * other value (more formally, it should be the identity element for the
 * reduction). Most common reductions have these properties; for example,
 * computing a sum with basis 0 or a minimum with basis MAX_VALUE.
 *
 * <p>
 * Search and transformation functions provided as arguments should similarly
 * return null to indicate the lack of any result (in which case it is not
 * used). In the case of mapped reductions, this also enables transformations to
 * serve as filters, returning null (or, in the case of primitive
 * specializations, the identity basis) if the element should not be combined.
 * You can create compound transformations and filterings by composing them
 * yourself under this "null means there is nothing there now" rule before using
 * them in search or reduce operations.
 *
 * <p>
 * Methods accepting and/or returning Entry arguments maintain key-value
 * associations. They may be useful for example when finding the key for the
 * greatest value. Note that "plain" Entry arguments can be supplied using
 * {@code new
 * AbstractMap.SimpleEntry(k,v)}.
 *
 * <p>
 * Bulk operations may complete abruptly, throwing an exception encountered in
 * the application of a supplied function. Bear in mind when handling such
 * exceptions that other concurrently executing functions could also have thrown
 * exceptions, or would have done so if the first exception had not occurred.
 *
 * <p>
 * Speedups for parallel compared to sequential forms are common but not
 * guaranteed. Parallel operations involving brief functions on small maps may
 * execute more slowly than sequential forms if the underlying work to
 * parallelize the computation is more expensive than the computation itself.
 * Similarly, parallelization may not lead to much actual parallelism if all
 * processors are busy performing unrelated tasks.
 *
 * <p>
 * All arguments to all task methods must be non-null.
 *
 * <p>
 * This class is a member of the <a href="{@docRoot}
 * /../technotes/guides/collections/index.html"> Java Collections Framework</a>.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <K>
 *            the type of keys maintained by this map
 * @param <V>
 *            the type of mapped values
 */
public class InternalConcurrentHashMap
{
	/*
	 * Overview:
	 *
	 * The primary design goal of this hash table is to maintain concurrent
	 * readability (typically method get(), but also iterators and related
	 * methods) while minimizing update contention. Secondary goals are to keep
	 * space consumption about the same or better than java.util.HashMap, and to
	 * support high initial insertion rates on an empty table by many threads.
	 *
	 * This map usually acts as a binned (bucketed) hash table. Each key-value
	 * mapping is held in a Node. Most nodes are instances of the basic Node
	 * class with hash, key, value, and next fields. However, various subclasses
	 * exist: TreeNodes are arranged in balanced trees, not lists. TreeBins hold
	 * the roots of sets of TreeNodes. ForwardingNodes are placed at the heads
	 * of bins during resizing. ReservationNodes are used as placeholders while
	 * establishing values in computeIfAbsent and related methods. The types
	 * TreeBin, ForwardingNode, and ReservationNode do not hold normal user
	 * keys, values, or hashes, and are readily distinguishable during search
	 * etc because they have negative hash fields and null key and value fields.
	 * (These special nodes are either uncommon or transient, so the impact of
	 * carrying around some unused fields is insignificant.)
	 *
	 * The table is lazily initialized to a power-of-two size upon the first
	 * insertion. Each bin in the table normally contains a list of Nodes (most
	 * often, the list has only zero or one Node). Table accesses require
	 * volatile/atomic reads, writes, and CASes. Because there is no other way
	 * to arrange this without adding further indirections, we use intrinsics
	 * (sun.misc.Unsafe) operations.
	 *
	 * We use the top (sign) bit of Node hash fields for control purposes -- it
	 * is available anyway because of addressing constraints. Nodes with
	 * negative hash fields are specially handled or ignored in map methods.
	 *
	 * Insertion (via put or its variants) of the first node in an empty bin is
	 * performed by just CASing it to the bin. This is by far the most common
	 * case for put operations under most key/hash distributions. Other update
	 * operations (insert, delete, and replace) require locks. We do not want to
	 * waste the space required to associate a distinct lock object with each
	 * bin, so instead use the first node of a bin list itself as a lock.
	 * Locking support for these locks relies on builtin "synchronized"
	 * monitors.
	 *
	 * Using the first node of a list as a lock does not by itself suffice
	 * though: When a node is locked, any update must first validate that it is
	 * still the first node after locking it, and retry if not. Because new
	 * nodes are always appended to lists, once a node is first in a bin, it
	 * remains first until deleted or the bin becomes invalidated (upon
	 * resizing).
	 *
	 * The main disadvantage of per-bin locks is that other update operations on
	 * other nodes in a bin list protected by the same lock can stall, for
	 * example when user equals() or mapping functions take a long time.
	 * However, statistically, under random hash codes, this is not a common
	 * problem. Ideally, the frequency of nodes in bins follows a Poisson
	 * distribution (http://en.wikipedia.org/wiki/Poisson_distribution) with a
	 * parameter of about 0.5 on average, given the resizing threshold of 0.75,
	 * although with a large variance because of resizing granularity. Ignoring
	 * variance, the expected occurrences of list size k are (exp(-0.5) *
	 * pow(0.5, k) / factorial(k)). The first values are:
	 *
	 * 0: 0.60653066 1: 0.30326533 2: 0.07581633 3: 0.01263606 4: 0.00157952 5:
	 * 0.00015795 6: 0.00001316 7: 0.00000094 8: 0.00000006 more: less than 1 in
	 * ten million
	 *
	 * Lock contention probability for two threads accessing distinct elements
	 * is roughly 1 / (8 * #elements) under random hashes.
	 *
	 * Actual hash code distributions encountered in practice sometimes deviate
	 * significantly from uniform randomness. This includes the case when N >
	 * (1<<30), so some keys MUST collide. Similarly for dumb or hostile usages
	 * in which multiple keys are designed to have identical hash codes or ones
	 * that differs only in masked-out high bits. So we use a secondary strategy
	 * that applies when the number of nodes in a bin exceeds a threshold. These
	 * TreeBins use a balanced tree to hold nodes (a specialized form of
	 * red-black trees), bounding search time to O(log N). Each search step in a
	 * TreeBin is at least twice as slow as in a regular list, but given that N
	 * cannot exceed (1<<64) (before running out of addresses) this bounds
	 * search steps, lock hold times, etc, to reasonable constants (roughly 100
	 * nodes inspected per operation worst case) so long as keys are Comparable
	 * (which is very common -- String, Long, etc). TreeBin nodes (TreeNodes)
	 * also maintain the same "next" traversal pointers as regular nodes, so can
	 * be traversed in iterators in the same way.
	 *
	 * The table is resized when occupancy exceeds a percentage threshold
	 * (nominally, 0.75, but see below). Any thread noticing an overfull bin may
	 * assist in resizing after the initiating thread allocates and sets up the
	 * replacement array. However, rather than stalling, these other threads may
	 * proceed with insertions etc. The use of TreeBins shields us from the
	 * worst case effects of overfilling while resizes are in progress. Resizing
	 * proceeds by transferring bins, one by one, from the table to the next
	 * table. However, threads claim small blocks of indices to transfer (via
	 * field transferIndex) before doing so, reducing contention. A generation
	 * stamp in field sizeCtl ensures that resizings do not overlap. Because we
	 * are using power-of-two expansion, the elements from each bin must either
	 * stay at same index, or move with a power of two offset. We eliminate
	 * unnecessary node creation by catching cases where old nodes can be reused
	 * because their next fields won't change. On average, only about one-sixth
	 * of them need cloning when a table doubles. The nodes they replace will be
	 * garbage collectable as soon as they are no longer referenced by any
	 * reader thread that may be in the midst of concurrently traversing table.
	 * Upon transfer, the old table bin contains only a special forwarding node
	 * (with hash field "MOVED") that contains the next table as its key. On
	 * encountering a forwarding node, access and update operations restart,
	 * using the new table.
	 *
	 * Each bin transfer requires its bin lock, which can stall waiting for
	 * locks while resizing. However, because other threads can join in and help
	 * resize rather than contend for locks, average aggregate waits become
	 * shorter as resizing progresses. The transfer operation must also ensure
	 * that all accessible bins in both the old and new table are usable by any
	 * traversal. This is arranged in part by proceeding from the last bin
	 * (table.length - 1) up towards the first. Upon seeing a forwarding node,
	 * traversals (see class Traverser) arrange to move to the new table without
	 * revisiting nodes. To ensure that no intervening nodes are skipped even
	 * when moved out of order, a stack (see class TableStack) is created on
	 * first encounter of a forwarding node during a traversal, to maintain its
	 * place if later processing the current table. The need for these
	 * save/restore mechanics is relatively rare, but when one forwarding node
	 * is encountered, typically many more will be. So Traversers use a simple
	 * caching scheme to avoid creating so many new TableStack nodes. (Thanks to
	 * Peter Levart for suggesting use of a stack here.)
	 *
	 * The traversal scheme also applies to partial traversals of ranges of bins
	 * (via an alternate Traverser constructor) to support partitioned aggregate
	 * operations. Also, read-only operations give up if ever forwarded to a
	 * null table, which provides support for shutdown-style clearing, which is
	 * also not currently implemented.
	 *
	 * Lazy table initialization minimizes footprint until first use, and also
	 * avoids resizings when the first operation is from a putAll, constructor
	 * with map argument, or deserialization. These cases attempt to override
	 * the initial capacity settings, but harmlessly fail to take effect in
	 * cases of races.
	 *
	 * The element count is maintained using a specialization of LongAdder. We
	 * need to incorporate a specialization rather than just use a LongAdder in
	 * order to access implicit contention-sensing that leads to creation of
	 * multiple CounterCells. The counter mechanics avoid contention on updates
	 * but can encounter cache thrashing if read too frequently during
	 * concurrent access. To avoid reading so often, resizing under contention
	 * is attempted only upon adding to a bin already holding two or more nodes.
	 * Under uniform hash distributions, the probability of this occurring at
	 * threshold is around 13%, meaning that only about 1 in 8 puts check
	 * threshold (and after resizing, many fewer do so).
	 *
	 * TreeBins use a special form of comparison for search and related
	 * operations (which is the main reason we cannot use existing collections
	 * such as TreeMaps). TreeBins contain Comparable elements, but may contain
	 * others, as well as elements that are Comparable but not necessarily
	 * Comparable for the same T, so we cannot invoke compareTo among them. To
	 * handle this, the tree is ordered primarily by hash value, then by
	 * Comparable.compareTo order if applicable. On lookup at a node, if
	 * elements are not comparable or compare as 0 then both left and right
	 * children may need to be searched in the case of tied hash values. (This
	 * corresponds to the full list search that would be necessary if all
	 * elements were non-Comparable and had tied hashes.) On insertion, to keep
	 * a total ordering (or as close as is required here) across rebalancings,
	 * we compare classes and identityHashCodes as tie-breakers. The red-black
	 * balancing code is updated from pre-jdk-collections
	 * (http://gee.cs.oswego.edu/dl/classes/collections/RBCell.java) based in
	 * turn on Cormen, Leiserson, and Rivest "Introduction to Algorithms" (CLR).
	 *
	 * TreeBins also require an additional locking mechanism. While list
	 * traversal is always possible by readers even during updates, tree
	 * traversal is not, mainly because of tree-rotations that may change the
	 * root node and/or its linkages. TreeBins include a simple read-write lock
	 * mechanism parasitic on the main bin-synchronization strategy: Structural
	 * adjustments associated with an insertion or removal are already
	 * bin-locked (and so cannot conflict with other writers) but must wait for
	 * ongoing readers to finish. Since there can be only one such waiter, we
	 * use a simple scheme using a single "waiter" field to block writers.
	 * However, readers need never block. If the root lock is held, they proceed
	 * along the slow traversal path (via next-pointers) until the lock becomes
	 * available or the list is exhausted, whichever comes first. These cases
	 * are not fast, but maximize aggregate expected throughput.
	 *
	 * Maintaining API and serialization compatibility with previous versions of
	 * this class introduces several oddities. Mainly: We leave untouched but
	 * unused constructor arguments refering to concurrencyLevel. We accept a
	 * loadFactor constructor argument, but apply it only to initial table
	 * capacity (which is the only time that we can guarantee to honor it.) We
	 * also declare an unused "Segment" class that is instantiated in minimal
	 * form only when serializing.
	 *
	 * Also, solely for compatibility with previous versions of this class, it
	 * extends AbstractMap, even though all of its methods are overridden, so it
	 * is just useless baggage.
	 *
	 * This file is organized to make things a little easier to follow while
	 * reading than they might otherwise: First the main static declarations and
	 * utilities, then fields, then main public methods (with a few factorings
	 * of multiple public methods into internal ones), then sizing methods,
	 * trees, traversers, and bulk operations.
	 */

	/* ---------------- Constants -------------- */

	/**
	 * The largest possible table capacity. This value must be exactly 1<<30 to
	 * stay within Java array allocation and indexing bounds for power of two
	 * table sizes, and is further required because the top two bits of 32bit
	 * hash fields are used for control purposes.
	 */
	private static final int MAXIMUM_CAPACITY = 1 << 30;

	/**
	 * The default initial table capacity. Must be a power of 2 (i.e., at least
	 * 1) and at most MAXIMUM_CAPACITY.
	 */
	private static final int DEFAULT_CAPACITY = 16;

	/**
	 * The largest possible (non-power of two) array size. Needed by toArray and
	 * related methods.
	 */
	static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

	/**
	 * The default concurrency level for this table. Unused but defined for
	 * compatibility with previous versions of this class.
	 */
	private static final int DEFAULT_CONCURRENCY_LEVEL = 16;

	/**
	 * The load factor for this table. Overrides of this value in constructors
	 * affect only the initial table capacity. The actual floating point value
	 * isn't normally used -- it is simpler to use expressions such as
	 * {@code n - (n >>> 2)} for the associated resizing threshold.
	 */
	private static final float LOAD_FACTOR = 0.75f;

	/**
	 * The bin count threshold for using a tree rather than list for a bin. Bins
	 * are converted to trees when adding an element to a bin with at least this
	 * many nodes. The value must be greater than 2, and should be at least 8 to
	 * mesh with assumptions in tree removal about conversion back to plain bins
	 * upon shrinkage.
	 */
	static final int TREEIFY_THRESHOLD = 8;

	/**
	 * The bin count threshold for untreeifying a (split) bin during a resize
	 * operation. Should be less than TREEIFY_THRESHOLD, and at most 6 to mesh
	 * with shrinkage detection under removal.
	 */
	static final int UNTREEIFY_THRESHOLD = 6;

	/**
	 * The smallest table capacity for which bins may be treeified. (Otherwise
	 * the table is resized if too many nodes in a bin.) The value should be at
	 * least 4 * TREEIFY_THRESHOLD to avoid conflicts between resizing and
	 * treeification thresholds.
	 */
	static final int MIN_TREEIFY_CAPACITY = 64;

	/**
	 * Minimum number of rebinnings per transfer step. Ranges are subdivided to
	 * allow multiple resizer threads. This value serves as a lower bound to
	 * avoid resizers encountering excessive memory contention. The value should
	 * be at least DEFAULT_CAPACITY.
	 */
	private static final int MIN_TRANSFER_STRIDE = 16;

	/**
	 * The number of bits used for generation stamp in sizeCtl. Must be at least
	 * 6 for 32bit arrays.
	 */
	private static int RESIZE_STAMP_BITS = 16;

	/**
	 * The maximum number of threads that can help resize. Must fit in 32 -
	 * RESIZE_STAMP_BITS bits.
	 */
	private static final int MAX_RESIZERS = (1 << (32 - RESIZE_STAMP_BITS)) - 1;

	/**
	 * The bit shift for recording size stamp in sizeCtl.
	 */
	private static final int RESIZE_STAMP_SHIFT = 32 - RESIZE_STAMP_BITS;

	/*
	 * Encodings for Node hash fields. See above for explanation.
	 */
	static final int MOVED = -1; // hash for forwarding nodes
	static final int TREEBIN = -2; // hash for roots of trees
	static final int RESERVED = -3; // hash for transient reservations
	static final int HASH_BITS = 0x7fffffff; // usable bits of normal node hash

	/** Number of CPUS, to place bounds on some sizings */
	static final int NCPU = Runtime.getRuntime().availableProcessors();

	/** For serialization compatibility. */
	private static final ObjectStreamField[] serialPersistentFields = { new ObjectStreamField("segments", Segment[].class), new ObjectStreamField("segmentMask", Integer.TYPE), new ObjectStreamField("segmentShift", Integer.TYPE) };

	/* ---------------- Nodes -------------- */

	/**
	 * Base class for bulk tasks. Repeats some fields and code from class
	 * Traverser, because we need to subclass CountedCompleter.
	 */
	/*
	 * @SuppressWarnings("serial") abstract static class BulkTask<R> extends
	 * CountedCompleter<R> { Node[] tab; // same as Traverser Node next;
	 * TableStack stack, spare; int index; int baseIndex; int baseLimit; final
	 * int baseSize; int batch; // split control
	 *
	 * BulkTask(BulkTask<?> par, int b, int i, int f, Node[] t) { super(par);
	 * this.batch = b; this.index = this.baseIndex = i; if ((this.tab = t) ==
	 * null) this.baseSize = this.baseLimit = 0; else if (par == null)
	 * this.baseSize = this.baseLimit = t.length; else { this.baseLimit = f;
	 * this.baseSize = par.baseSize; } }
	 */
	/**
	 * Same as Traverser version
	 */
	/*
	 * final Node advance() { Node e; if ((e = next) != null) e = e.next; for
	 * (;;) { Node[] t; int i, n; if (e != null) return next = e; if (baseIndex
	 * >= baseLimit || (t = tab) == null || (n = t.length) <= (i = index) || i <
	 * 0) return next = null; if ((e = tabAt(t, i)) != null && e.hash < 0) { if
	 * (e instanceof ForwardingNode) { tab = ((ForwardingNode)e).nextTable; e =
	 * null; pushState(t, i, n); continue; } else if (e instanceof TreeBin) e =
	 * ((TreeBin)e).first; else e = null; } if (stack != null) recoverState(n);
	 * else if ((index = i + baseSize) >= n) index = ++baseIndex; } }
	 *
	 * private void pushState(Node[] t, int i, int n) { TableStack s = spare; if
	 * (s != null) spare = s.next; else s = new TableStack(); s.tab = t;
	 * s.length = n; s.index = i; s.next = stack; stack = s; }
	 *
	 * private void recoverState(int n) { TableStack s; int len; while ((s =
	 * stack) != null && (index += (len = s.length)) >= n) { n = len; index =
	 * s.index; tab = s.tab; s.tab = null; TableStack next = s.next; s.next =
	 * spare; // save for reuse stack = next; spare = s; } if (s == null &&
	 * (index += baseSize) >= n) index = ++baseIndex; } }
	 */

	/*
	 * Task classes. Coded in a regular but ugly format/style to simplify checks
	 * that each variant differs in the right way from others. The null
	 * screenings exist because compilers cannot tell that we've already
	 * null-checked task arguments, so we force simplest hoisted bypass to help
	 * avoid convoluted traps.
	 */
	/*
	 * static final class ForEachKeyTask extends BulkTask<Void> { final
	 * Consumer<? super K> action; ForEachKeyTask (BulkTask<K,V,?> p, int b, int
	 * i, int f, Node<K,V>[] t, Consumer<? super K> action) { super(p, b, i, f,
	 * t); this.action = action; } public final void compute() { final
	 * Consumer<? super K> action; if ((action = this.action) != null) { for
	 * (int i = baseIndex, f, h; batch > 0 && (h = ((f = baseLimit) + i) >>> 1)
	 * > i;) { addToPendingCount(1); new ForEachKeyTask<K,V> (this, batch >>>=
	 * 1, baseLimit = h, f, tab, action).fork(); } for (Node<K,V> p; (p =
	 * advance()) != null;) action.accept(p.key); propagateCompletion(); } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class ForEachValueTask<K,V>
	 * extends BulkTask<K,V,Void> { final Consumer<? super V> action;
	 * ForEachValueTask (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
	 * Consumer<? super V> action) { super(p, b, i, f, t); this.action = action;
	 * } public final void compute() { final Consumer<? super V> action; if
	 * ((action = this.action) != null) { for (int i = baseIndex, f, h; batch >
	 * 0 && (h = ((f = baseLimit) + i) >>> 1) > i;) { addToPendingCount(1); new
	 * ForEachValueTask<K,V> (this, batch >>>= 1, baseLimit = h, f, tab,
	 * action).fork(); } for (Node<K,V> p; (p = advance()) != null;)
	 * action.accept(p.val); propagateCompletion(); } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class ForEachEntryTask extends
	 * BulkTask<Void> { final Consumer<? super Entry> action; ForEachEntryTask
	 * (BulkTask<?> p, int b, int i, int f, Node[] t, Consumer<? super Entry>
	 * action) { super(p, b, i, f, t); this.action = action; } public final void
	 * compute() { final Consumer<? super Entry> action; if ((action =
	 * this.action) != null) { for (int i = baseIndex, f, h; batch > 0 && (h =
	 * ((f = baseLimit) + i) >>> 1) > i;) { addToPendingCount(1); new
	 * ForEachEntryTask (this, batch >>>= 1, baseLimit = h, f, tab,
	 * action).fork(); } for (Node p; (p = advance()) != null; )
	 * action.accept(p); propagateCompletion(); } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class ForEachMappingTask extends
	 * BulkTask<Void> { final BiConsumer<? super K, ? super V> action;
	 * ForEachMappingTask (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[]
	 * t, BiConsumer<? super K,? super V> action) { super(p, b, i, f, t);
	 * this.action = action; } public final void compute() { final BiConsumer<?
	 * super K, ? super V> action; if ((action = this.action) != null) { for
	 * (int i = baseIndex, f, h; batch > 0 && (h = ((f = baseLimit) + i) >>> 1)
	 * > i;) { addToPendingCount(1); new ForEachMappingTask<K,V> (this, batch
	 * >>>= 1, baseLimit = h, f, tab, action).fork(); } for (Node<K,V> p; (p =
	 * advance()) != null; ) action.accept(p.key, p.val); propagateCompletion();
	 * } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class
	 * ForEachTransformedKeyTask<K,V,U> extends BulkTask<K,V,Void> { final
	 * Function<? super K, ? extends U> transformer; final Consumer<? super U>
	 * action; ForEachTransformedKeyTask (BulkTask<K,V,?> p, int b, int i, int
	 * f, Node<K,V>[] t, Function<? super K, ? extends U> transformer,
	 * Consumer<? super U> action) { super(p, b, i, f, t); this.transformer =
	 * transformer; this.action = action; } public final void compute() { final
	 * Function<? super K, ? extends U> transformer; final Consumer<? super U>
	 * action; if ((transformer = this.transformer) != null && (action =
	 * this.action) != null) { for (int i = baseIndex, f, h; batch > 0 && (h =
	 * ((f = baseLimit) + i) >>> 1) > i;) { addToPendingCount(1); new
	 * ForEachTransformedKeyTask<K,V,U> (this, batch >>>= 1, baseLimit = h, f,
	 * tab, transformer, action).fork(); } for (Node<K,V> p; (p = advance()) !=
	 * null; ) { U u; if ((u = transformer.apply(p.key)) != null)
	 * action.accept(u); } propagateCompletion(); } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class
	 * ForEachTransformedValueTask<K,V,U> extends BulkTask<K,V,Void> { final
	 * Function<? super V, ? extends U> transformer; final Consumer<? super U>
	 * action; ForEachTransformedValueTask (BulkTask<K,V,?> p, int b, int i, int
	 * f, Node<K,V>[] t, Function<? super V, ? extends U> transformer,
	 * Consumer<? super U> action) { super(p, b, i, f, t); this.transformer =
	 * transformer; this.action = action; } public final void compute() { final
	 * Function<? super V, ? extends U> transformer; final Consumer<? super U>
	 * action; if ((transformer = this.transformer) != null && (action =
	 * this.action) != null) { for (int i = baseIndex, f, h; batch > 0 && (h =
	 * ((f = baseLimit) + i) >>> 1) > i;) { addToPendingCount(1); new
	 * ForEachTransformedValueTask<K,V,U> (this, batch >>>= 1, baseLimit = h, f,
	 * tab, transformer, action).fork(); } for (Node<K,V> p; (p = advance()) !=
	 * null; ) { U u; if ((u = transformer.apply(p.val)) != null)
	 * action.accept(u); } propagateCompletion(); } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class
	 * ForEachTransformedEntryTask<U> extends BulkTask<Void> { final
	 * Function<Map.Entry, ? extends U> transformer; final Consumer<? super U>
	 * action; ForEachTransformedEntryTask (BulkTask<?> p, int b, int i, int f,
	 * Node[] t, Function<Map.Entry, ? extends U> transformer, Consumer<? super
	 * U> action) { super(p, b, i, f, t); this.transformer = transformer;
	 * this.action = action; } public final void compute() { final
	 * Function<Map.Entry, ? extends U> transformer; final Consumer<? super U>
	 * action; if ((transformer = this.transformer) != null && (action =
	 * this.action) != null) { for (int i = baseIndex, f, h; batch > 0 && (h =
	 * ((f = baseLimit) + i) >>> 1) > i;) { addToPendingCount(1); new
	 * ForEachTransformedEntryTask<U> (this, batch >>>= 1, baseLimit = h, f,
	 * tab, transformer, action).fork(); } for (Node p; (p = advance()) != null;
	 * ) { U u; if ((u = transformer.apply(p)) != null) action.accept(u); }
	 * propagateCompletion(); } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class
	 * ForEachTransformedMappingTask<U> extends BulkTask<Void> { final
	 * BiFunction<? super K, ? super V, ? extends U> transformer; final
	 * Consumer<? super U> action; ForEachTransformedMappingTask
	 * (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t, BiFunction<?
	 * super K, ? super V, ? extends U> transformer, Consumer<? super U> action)
	 * { super(p, b, i, f, t); this.transformer = transformer; this.action =
	 * action; } public final void compute() { final BiFunction<? super K, ?
	 * super V, ? extends U> transformer; final Consumer<? super U> action; if
	 * ((transformer = this.transformer) != null && (action = this.action) !=
	 * null) { for (int i = baseIndex, f, h; batch > 0 && (h = ((f = baseLimit)
	 * + i) >>> 1) > i;) { addToPendingCount(1); new
	 * ForEachTransformedMappingTask<K,V,U> (this, batch >>>= 1, baseLimit = h,
	 * f, tab, transformer, action).fork(); } for (Node<K,V> p; (p = advance())
	 * != null; ) { U u; if ((u = transformer.apply(p.key, p.val)) != null)
	 * action.accept(u); } propagateCompletion(); } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class SearchKeysTask<K,V,U>
	 * extends BulkTask<K,V,U> { final Function<? super K, ? extends U>
	 * searchFunction; final AtomicReference<U> result; SearchKeysTask
	 * (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t, Function<? super
	 * K, ? extends U> searchFunction, AtomicReference<U> result) { super(p, b,
	 * i, f, t); this.searchFunction = searchFunction; this.result = result; }
	 * public final U getRawResult() { return result.get(); } public final void
	 * compute() { final Function<? super K, ? extends U> searchFunction; final
	 * AtomicReference<U> result; if ((searchFunction = this.searchFunction) !=
	 * null && (result = this.result) != null) { for (int i = baseIndex, f, h;
	 * batch > 0 && (h = ((f = baseLimit) + i) >>> 1) > i;) { if (result.get()
	 * != null) return; addToPendingCount(1); new SearchKeysTask<K,V,U> (this,
	 * batch >>>= 1, baseLimit = h, f, tab, searchFunction, result).fork(); }
	 * while (result.get() == null) { U u; Node<K,V> p; if ((p = advance()) ==
	 * null) { propagateCompletion(); break; } if ((u =
	 * searchFunction.apply(p.key)) != null) { if (result.compareAndSet(null,
	 * u)) quietlyCompleteRoot(); break; } } } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class SearchValuesTask<K,V,U>
	 * extends BulkTask<K,V,U> { final Function<? super V, ? extends U>
	 * searchFunction; final AtomicReference<U> result; SearchValuesTask
	 * (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t, Function<? super
	 * V, ? extends U> searchFunction, AtomicReference<U> result) { super(p, b,
	 * i, f, t); this.searchFunction = searchFunction; this.result = result; }
	 * public final U getRawResult() { return result.get(); } public final void
	 * compute() { final Function<? super V, ? extends U> searchFunction; final
	 * AtomicReference<U> result; if ((searchFunction = this.searchFunction) !=
	 * null && (result = this.result) != null) { for (int i = baseIndex, f, h;
	 * batch > 0 && (h = ((f = baseLimit) + i) >>> 1) > i;) { if (result.get()
	 * != null) return; addToPendingCount(1); new SearchValuesTask<K,V,U> (this,
	 * batch >>>= 1, baseLimit = h, f, tab, searchFunction, result).fork(); }
	 * while (result.get() == null) { U u; Node<K,V> p; if ((p = advance()) ==
	 * null) { propagateCompletion(); break; } if ((u =
	 * searchFunction.apply(p.val)) != null) { if (result.compareAndSet(null,
	 * u)) quietlyCompleteRoot(); break; } } } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class SearchEntriesTask<U>
	 * extends BulkTask<U> { final Function<Entry, ? extends U> searchFunction;
	 * final AtomicReference<U> result; SearchEntriesTask (BulkTask<?> p, int b,
	 * int i, int f, Node[] t, Function<Entry, ? extends U> searchFunction,
	 * AtomicReference<U> result) { super(p, b, i, f, t); this.searchFunction =
	 * searchFunction; this.result = result; } public final U getRawResult() {
	 * return result.get(); } public final void compute() { final
	 * Function<Entry, ? extends U> searchFunction; final AtomicReference<U>
	 * result; if ((searchFunction = this.searchFunction) != null && (result =
	 * this.result) != null) { for (int i = baseIndex, f, h; batch > 0 && (h =
	 * ((f = baseLimit) + i) >>> 1) > i;) { if (result.get() != null) return;
	 * addToPendingCount(1); new SearchEntriesTask<U> (this, batch >>>= 1,
	 * baseLimit = h, f, tab, searchFunction, result).fork(); } while
	 * (result.get() == null) { U u; Node p; if ((p = advance()) == null) {
	 * propagateCompletion(); break; } if ((u = searchFunction.apply(p)) !=
	 * null) { if (result.compareAndSet(null, u)) quietlyCompleteRoot(); return;
	 * } } } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class SearchMappingsTask<U>
	 * extends BulkTask<K,V,U> { final BiFunction<? super K, ? super V, ?
	 * extends U> searchFunction; final AtomicReference<U> result;
	 * SearchMappingsTask (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[]
	 * t, BiFunction<? super K, ? super V, ? extends U> searchFunction,
	 * AtomicReference<U> result) { super(p, b, i, f, t); this.searchFunction =
	 * searchFunction; this.result = result; } public final U getRawResult() {
	 * return result.get(); } public final void compute() { final BiFunction<?
	 * super K, ? super V, ? extends U> searchFunction; final AtomicReference<U>
	 * result; if ((searchFunction = this.searchFunction) != null && (result =
	 * this.result) != null) { for (int i = baseIndex, f, h; batch > 0 && (h =
	 * ((f = baseLimit) + i) >>> 1) > i;) { if (result.get() != null) return;
	 * addToPendingCount(1); new SearchMappingsTask<K,V,U> (this, batch >>>= 1,
	 * baseLimit = h, f, tab, searchFunction, result).fork(); } while
	 * (result.get() == null) { U u; Node<K,V> p; if ((p = advance()) == null) {
	 * propagateCompletion(); break; } if ((u = searchFunction.apply(p.key,
	 * p.val)) != null) { if (result.compareAndSet(null, u))
	 * quietlyCompleteRoot(); break; } } } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class ReduceKeysTask<K,V>
	 * extends BulkTask<K,V,K> { final BiFunction<? super K, ? super K, ?
	 * extends K> reducer; K result; ReduceKeysTask<K,V> rights, nextRight;
	 * ReduceKeysTask (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
	 * ReduceKeysTask<K,V> nextRight, BiFunction<? super K, ? super K, ? extends
	 * K> reducer) { super(p, b, i, f, t); this.nextRight = nextRight;
	 * this.reducer = reducer; } public final K getRawResult() { return result;
	 * } public final void compute() { final BiFunction<? super K, ? super K, ?
	 * extends K> reducer; if ((reducer = this.reducer) != null) { for (int i =
	 * baseIndex, f, h; batch > 0 && (h = ((f = baseLimit) + i) >>> 1) > i;) {
	 * addToPendingCount(1); (rights = new ReduceKeysTask<K,V> (this, batch >>>=
	 * 1, baseLimit = h, f, tab, rights, reducer)).fork(); } K r = null; for
	 * (Node<K,V> p; (p = advance()) != null; ) { K u = p.key; r = (r == null) ?
	 * u : u == null ? r : reducer.apply(r, u); } result = r;
	 * CountedCompleter<?> c; for (c = firstComplete(); c != null; c =
	 * c.nextComplete()) {
	 *
	 * @SuppressWarnings("unchecked") ReduceKeysTask<K,V> t =
	 * (ReduceKeysTask<K,V>)c, s = t.rights; while (s != null) { K tr, sr; if
	 * ((sr = s.result) != null) t.result = (((tr = t.result) == null) ? sr :
	 * reducer.apply(tr, sr)); s = t.rights = s.nextRight; } } } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class ReduceValuesTask<K,V>
	 * extends BulkTask<K,V,V> { final BiFunction<? super V, ? super V, ?
	 * extends V> reducer; V result; ReduceValuesTask<K,V> rights, nextRight;
	 * ReduceValuesTask (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
	 * ReduceValuesTask<K,V> nextRight, BiFunction<? super V, ? super V, ?
	 * extends V> reducer) { super(p, b, i, f, t); this.nextRight = nextRight;
	 * this.reducer = reducer; } public final V getRawResult() { return result;
	 * } public final void compute() { final BiFunction<? super V, ? super V, ?
	 * extends V> reducer; if ((reducer = this.reducer) != null) { for (int i =
	 * baseIndex, f, h; batch > 0 && (h = ((f = baseLimit) + i) >>> 1) > i;) {
	 * addToPendingCount(1); (rights = new ReduceValuesTask<K,V> (this, batch
	 * >>>= 1, baseLimit = h, f, tab, rights, reducer)).fork(); } V r = null;
	 * for (Node<K,V> p; (p = advance()) != null; ) { V v = p.val; r = (r ==
	 * null) ? v : reducer.apply(r, v); } result = r; CountedCompleter<?> c; for
	 * (c = firstComplete(); c != null; c = c.nextComplete()) {
	 *
	 * @SuppressWarnings("unchecked") ReduceValuesTask<K,V> t =
	 * (ReduceValuesTask<K,V>)c, s = t.rights; while (s != null) { V tr, sr; if
	 * ((sr = s.result) != null) t.result = (((tr = t.result) == null) ? sr :
	 * reducer.apply(tr, sr)); s = t.rights = s.nextRight; } } } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class ReduceEntriesTask extends
	 * BulkTask<Map.Entry> { final BiFunction<Map.Entry, Map.Entry, ? extends
	 * Map.Entry> reducer; Map.Entry result; ReduceEntriesTask rights,
	 * nextRight; ReduceEntriesTask (BulkTask<?> p, int b, int i, int f, Node[]
	 * t, ReduceEntriesTask nextRight, BiFunction<Entry, Map.Entry, ? extends
	 * Map.Entry> reducer) { super(p, b, i, f, t); this.nextRight = nextRight;
	 * this.reducer = reducer; } public final Map.Entry getRawResult() { return
	 * result; } public final void compute() { final BiFunction<Map.Entry,
	 * Map.Entry, ? extends Map.Entry> reducer; if ((reducer = this.reducer) !=
	 * null) { for (int i = baseIndex, f, h; batch > 0 && (h = ((f = baseLimit)
	 * + i) >>> 1) > i;) { addToPendingCount(1); (rights = new ReduceEntriesTask
	 * (this, batch >>>= 1, baseLimit = h, f, tab, rights, reducer)).fork(); }
	 * Map.Entry r = null; for (Node p; (p = advance()) != null; ) r = (r ==
	 * null) ? p : reducer.apply(r, p); result = r; CountedCompleter<?> c; for
	 * (c = firstComplete(); c != null; c = c.nextComplete()) {
	 *
	 * @SuppressWarnings("unchecked") ReduceEntriesTask t =
	 * (ReduceEntriesTask)c, s = t.rights; while (s != null) { Map.Entry tr, sr;
	 * if ((sr = s.result) != null) t.result = (((tr = t.result) == null) ? sr :
	 * reducer.apply(tr, sr)); s = t.rights = s.nextRight; } } } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class MapReduceKeysTask<K,V,U>
	 * extends BulkTask<K,V,U> { final Function<? super K, ? extends U>
	 * transformer; final BiFunction<? super U, ? super U, ? extends U> reducer;
	 * U result; MapReduceKeysTask<K,V,U> rights, nextRight; MapReduceKeysTask
	 * (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
	 * MapReduceKeysTask<K,V,U> nextRight, Function<? super K, ? extends U>
	 * transformer, BiFunction<? super U, ? super U, ? extends U> reducer) {
	 * super(p, b, i, f, t); this.nextRight = nextRight; this.transformer =
	 * transformer; this.reducer = reducer; } public final U getRawResult() {
	 * return result; } public final void compute() { final Function<? super K,
	 * ? extends U> transformer; final BiFunction<? super U, ? super U, ?
	 * extends U> reducer; if ((transformer = this.transformer) != null &&
	 * (reducer = this.reducer) != null) { for (int i = baseIndex, f, h; batch >
	 * 0 && (h = ((f = baseLimit) + i) >>> 1) > i;) { addToPendingCount(1);
	 * (rights = new MapReduceKeysTask<K,V,U> (this, batch >>>= 1, baseLimit =
	 * h, f, tab, rights, transformer, reducer)).fork(); } U r = null; for
	 * (Node<K,V> p; (p = advance()) != null; ) { U u; if ((u =
	 * transformer.apply(p.key)) != null) r = (r == null) ? u : reducer.apply(r,
	 * u); } result = r; CountedCompleter<?> c; for (c = firstComplete(); c !=
	 * null; c = c.nextComplete()) {
	 *
	 * @SuppressWarnings("unchecked") MapReduceKeysTask<K,V,U> t =
	 * (MapReduceKeysTask<K,V,U>)c, s = t.rights; while (s != null) { U tr, sr;
	 * if ((sr = s.result) != null) t.result = (((tr = t.result) == null) ? sr :
	 * reducer.apply(tr, sr)); s = t.rights = s.nextRight; } } } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class MapReduceValuesTask<K,V,U>
	 * extends BulkTask<K,V,U> { final Function<? super V, ? extends U>
	 * transformer; final BiFunction<? super U, ? super U, ? extends U> reducer;
	 * U result; MapReduceValuesTask<K,V,U> rights, nextRight;
	 * MapReduceValuesTask (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[]
	 * t, MapReduceValuesTask<K,V,U> nextRight, Function<? super V, ? extends U>
	 * transformer, BiFunction<? super U, ? super U, ? extends U> reducer) {
	 * super(p, b, i, f, t); this.nextRight = nextRight; this.transformer =
	 * transformer; this.reducer = reducer; } public final U getRawResult() {
	 * return result; } public final void compute() { final Function<? super V,
	 * ? extends U> transformer; final BiFunction<? super U, ? super U, ?
	 * extends U> reducer; if ((transformer = this.transformer) != null &&
	 * (reducer = this.reducer) != null) { for (int i = baseIndex, f, h; batch >
	 * 0 && (h = ((f = baseLimit) + i) >>> 1) > i;) { addToPendingCount(1);
	 * (rights = new MapReduceValuesTask<K,V,U> (this, batch >>>= 1, baseLimit =
	 * h, f, tab, rights, transformer, reducer)).fork(); } U r = null; for
	 * (Node<K,V> p; (p = advance()) != null; ) { U u; if ((u =
	 * transformer.apply(p.val)) != null) r = (r == null) ? u : reducer.apply(r,
	 * u); } result = r; CountedCompleter<?> c; for (c = firstComplete(); c !=
	 * null; c = c.nextComplete()) {
	 *
	 * @SuppressWarnings("unchecked") MapReduceValuesTask<K,V,U> t =
	 * (MapReduceValuesTask<K,V,U>)c, s = t.rights; while (s != null) { U tr,
	 * sr; if ((sr = s.result) != null) t.result = (((tr = t.result) == null) ?
	 * sr : reducer.apply(tr, sr)); s = t.rights = s.nextRight; } } } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class MapReduceEntriesTask<U>
	 * extends BulkTask<U> { final Function<Map.Entry, ? extends U> transformer;
	 * final BiFunction<? super U, ? super U, ? extends U> reducer; U result;
	 * MapReduceEntriesTask<U> rights, nextRight; MapReduceEntriesTask
	 * (BulkTask<?> p, int b, int i, int f, Node[] t, MapReduceEntriesTask<U>
	 * nextRight, Function<Map.Entry, ? extends U> transformer, BiFunction<?
	 * super U, ? super U, ? extends U> reducer) { super(p, b, i, f, t);
	 * this.nextRight = nextRight; this.transformer = transformer; this.reducer
	 * = reducer; } public final U getRawResult() { return result; } public
	 * final void compute() { final Function<Map.Entry, ? extends U>
	 * transformer; final BiFunction<? super U, ? super U, ? extends U> reducer;
	 * if ((transformer = this.transformer) != null && (reducer = this.reducer)
	 * != null) { for (int i = baseIndex, f, h; batch > 0 && (h = ((f =
	 * baseLimit) + i) >>> 1) > i;) { addToPendingCount(1); (rights = new
	 * MapReduceEntriesTask<U> (this, batch >>>= 1, baseLimit = h, f, tab,
	 * rights, transformer, reducer)).fork(); } U r = null; for (Node p; (p =
	 * advance()) != null; ) { U u; if ((u = transformer.apply(p)) != null) r =
	 * (r == null) ? u : reducer.apply(r, u); } result = r; CountedCompleter<?>
	 * c; for (c = firstComplete(); c != null; c = c.nextComplete()) {
	 *
	 * @SuppressWarnings("unchecked") MapReduceEntriesTask<U> t =
	 * (MapReduceEntriesTask<U>)c, s = t.rights; while (s != null) { U tr, sr;
	 * if ((sr = s.result) != null) t.result = (((tr = t.result) == null) ? sr :
	 * reducer.apply(tr, sr)); s = t.rights = s.nextRight; } } } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class MapReduceMappingsTask<KU>
	 * extends BulkTask<K,V,U> { final BiFunction<? super K, ? super V, ?
	 * extends U> transformer; final BiFunction<? super U, ? super U, ? extends
	 * U> reducer; U result; MapReduceMappingsTask<K,V,U> rights, nextRight;
	 * MapReduceMappingsTask (BulkTask<K,V,?> p, int b, int i, int f,
	 * Node<K,V>[] t, MapReduceMappingsTask<K,V,U> nextRight, BiFunction<? super
	 * K, ? super V, ? extends U> transformer, BiFunction<? super U, ? super U,
	 * ? extends U> reducer) { super(p, b, i, f, t); this.nextRight = nextRight;
	 * this.transformer = transformer; this.reducer = reducer; } public final U
	 * getRawResult() { return result; } public final void compute() { final
	 * BiFunction<? super K, ? super V, ? extends U> transformer; final
	 * BiFunction<? super U, ? super U, ? extends U> reducer; if ((transformer =
	 * this.transformer) != null && (reducer = this.reducer) != null) { for (int
	 * i = baseIndex, f, h; batch > 0 && (h = ((f = baseLimit) + i) >>> 1) > i;)
	 * { addToPendingCount(1); (rights = new MapReduceMappingsTask<K,V,U> (this,
	 * batch >>>= 1, baseLimit = h, f, tab, rights, transformer,
	 * reducer)).fork(); } U r = null; for (Node<K,V> p; (p = advance()) !=
	 * null; ) { U u; if ((u = transformer.apply(p.key, p.val)) != null) r = (r
	 * == null) ? u : reducer.apply(r, u); } result = r; CountedCompleter<?> c;
	 * for (c = firstComplete(); c != null; c = c.nextComplete()) {
	 *
	 * @SuppressWarnings("unchecked") MapReduceMappingsTask<K,V,U> t =
	 * (MapReduceMappingsTask<K,V,U>)c, s = t.rights; while (s != null) { U tr,
	 * sr; if ((sr = s.result) != null) t.result = (((tr = t.result) == null) ?
	 * sr : reducer.apply(tr, sr)); s = t.rights = s.nextRight; } } } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class
	 * MapReduceKeysToDoubleTask<K,V> extends BulkTask<K,V,Double> { final
	 * ToDoubleFunction<? super K> transformer; final DoubleBinaryOperator
	 * reducer; final double basis; double result;
	 * MapReduceKeysToDoubleTask<K,V> rights, nextRight;
	 * MapReduceKeysToDoubleTask (BulkTask<K,V,?> p, int b, int i, int f,
	 * Node<K,V>[] t, MapReduceKeysToDoubleTask<K,V> nextRight,
	 * ToDoubleFunction<? super K> transformer, double basis,
	 * DoubleBinaryOperator reducer) { super(p, b, i, f, t); this.nextRight =
	 * nextRight; this.transformer = transformer; this.basis = basis;
	 * this.reducer = reducer; } public final Double getRawResult() { return
	 * result; } public final void compute() { final ToDoubleFunction<? super K>
	 * transformer; final DoubleBinaryOperator reducer; if ((transformer =
	 * this.transformer) != null && (reducer = this.reducer) != null) { double r
	 * = this.basis; for (int i = baseIndex, f, h; batch > 0 && (h = ((f =
	 * baseLimit) + i) >>> 1) > i;) { addToPendingCount(1); (rights = new
	 * MapReduceKeysToDoubleTask<K,V> (this, batch >>>= 1, baseLimit = h, f,
	 * tab, rights, transformer, r, reducer)).fork(); } for (Node<K,V> p; (p =
	 * advance()) != null; ) r = reducer.applyAsDouble(r,
	 * transformer.applyAsDouble(p.key)); result = r; CountedCompleter<?> c; for
	 * (c = firstComplete(); c != null; c = c.nextComplete()) {
	 *
	 * @SuppressWarnings("unchecked") MapReduceKeysToDoubleTask<K,V> t =
	 * (MapReduceKeysToDoubleTask<K,V>)c, s = t.rights; while (s != null) {
	 * t.result = reducer.applyAsDouble(t.result, s.result); s = t.rights =
	 * s.nextRight; } } } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class
	 * MapReduceValuesToDoubleTask<K,V> extends BulkTask<K,V,Double> { final
	 * ToDoubleFunction<? super V> transformer; final DoubleBinaryOperator
	 * reducer; final double basis; double result;
	 * MapReduceValuesToDoubleTask<K,V> rights, nextRight;
	 * MapReduceValuesToDoubleTask (BulkTask<K,V,?> p, int b, int i, int f,
	 * Node<K,V>[] t, MapReduceValuesToDoubleTask<K,V> nextRight,
	 * ToDoubleFunction<? super V> transformer, double basis,
	 * DoubleBinaryOperator reducer) { super(p, b, i, f, t); this.nextRight =
	 * nextRight; this.transformer = transformer; this.basis = basis;
	 * this.reducer = reducer; } public final Double getRawResult() { return
	 * result; } public final void compute() { final ToDoubleFunction<? super V>
	 * transformer; final DoubleBinaryOperator reducer; if ((transformer =
	 * this.transformer) != null && (reducer = this.reducer) != null) { double r
	 * = this.basis; for (int i = baseIndex, f, h; batch > 0 && (h = ((f =
	 * baseLimit) + i) >>> 1) > i;) { addToPendingCount(1); (rights = new
	 * MapReduceValuesToDoubleTask<K,V> (this, batch >>>= 1, baseLimit = h, f,
	 * tab, rights, transformer, r, reducer)).fork(); } for (Node<K,V> p; (p =
	 * advance()) != null; ) r = reducer.applyAsDouble(r,
	 * transformer.applyAsDouble(p.val)); result = r; CountedCompleter<?> c; for
	 * (c = firstComplete(); c != null; c = c.nextComplete()) {
	 *
	 * @SuppressWarnings("unchecked") MapReduceValuesToDoubleTask<K,V> t =
	 * (MapReduceValuesToDoubleTask<K,V>)c, s = t.rights; while (s != null) {
	 * t.result = reducer.applyAsDouble(t.result, s.result); s = t.rights =
	 * s.nextRight; } } } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class
	 * MapReduceEntriesToDoubleTask extends BulkTask<Double> { final
	 * ToDoubleFunction<Map.Entry> transformer; final DoubleBinaryOperator
	 * reducer; final double basis; double result; MapReduceEntriesToDoubleTask
	 * rights, nextRight; MapReduceEntriesToDoubleTask (BulkTask<?> p, int b,
	 * int i, int f, Node[] t, MapReduceEntriesToDoubleTask nextRight,
	 * ToDoubleFunction<Map.Entry> transformer, double basis,
	 * DoubleBinaryOperator reducer) { super(p, b, i, f, t); this.nextRight =
	 * nextRight; this.transformer = transformer; this.basis = basis;
	 * this.reducer = reducer; } public final Double getRawResult() { return
	 * result; } public final void compute() { final ToDoubleFunction<Map.Entry>
	 * transformer; final DoubleBinaryOperator reducer; if ((transformer =
	 * this.transformer) != null && (reducer = this.reducer) != null) { double r
	 * = this.basis; for (int i = baseIndex, f, h; batch > 0 && (h = ((f =
	 * baseLimit) + i) >>> 1) > i;) { addToPendingCount(1); (rights = new
	 * MapReduceEntriesToDoubleTask (this, batch >>>= 1, baseLimit = h, f, tab,
	 * rights, transformer, r, reducer)).fork(); } for (Node p; (p = advance())
	 * != null; ) r = reducer.applyAsDouble(r, transformer.applyAsDouble(p));
	 * result = r; CountedCompleter<?> c; for (c = firstComplete(); c != null; c
	 * = c.nextComplete()) {
	 *
	 * @SuppressWarnings("unchecked") MapReduceEntriesToDoubleTask t =
	 * (MapReduceEntriesToDoubleTask)c, s = t.rights; while (s != null) {
	 * t.result = reducer.applyAsDouble(t.result, s.result); s = t.rights =
	 * s.nextRight; } } } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class
	 * MapReduceMappingsToDoubleTask extends BulkTask<Double> { final
	 * ToDoubleBiFunction<? super K, ? super V> transformer; final
	 * DoubleBinaryOperator reducer; final double basis; double result;
	 * MapReduceMappingsToDoubleTask<K,V> rights, nextRight;
	 * MapReduceMappingsToDoubleTask (BulkTask<K,V,?> p, int b, int i, int f,
	 * Node<K,V>[] t, MapReduceMappingsToDoubleTask<K,V> nextRight,
	 * ToDoubleBiFunction<? super K, ? super V> transformer, double basis,
	 * DoubleBinaryOperator reducer) { super(p, b, i, f, t); this.nextRight =
	 * nextRight; this.transformer = transformer; this.basis = basis;
	 * this.reducer = reducer; } public final Double getRawResult() { return
	 * result; } public final void compute() { final ToDoubleBiFunction<? super
	 * K, ? super V> transformer; final DoubleBinaryOperator reducer; if
	 * ((transformer = this.transformer) != null && (reducer = this.reducer) !=
	 * null) { double r = this.basis; for (int i = baseIndex, f, h; batch > 0 &&
	 * (h = ((f = baseLimit) + i) >>> 1) > i;) { addToPendingCount(1); (rights =
	 * new MapReduceMappingsToDoubleTask<K,V> (this, batch >>>= 1, baseLimit =
	 * h, f, tab, rights, transformer, r, reducer)).fork(); } for (Node<K,V> p;
	 * (p = advance()) != null; ) r = reducer.applyAsDouble(r,
	 * transformer.applyAsDouble(p.key, p.val)); result = r; CountedCompleter<?>
	 * c; for (c = firstComplete(); c != null; c = c.nextComplete()) {
	 *
	 * @SuppressWarnings("unchecked") MapReduceMappingsToDoubleTask<K,V> t =
	 * (MapReduceMappingsToDoubleTask<K,V>)c, s = t.rights; while (s != null) {
	 * t.result = reducer.applyAsDouble(t.result, s.result); s = t.rights =
	 * s.nextRight; } } } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class MapReduceKeysToLongTask
	 * extends BulkTask<Long> { final ToLongFunction<? super K> transformer;
	 * final LongBinaryOperator reducer; final long basis; long result;
	 * MapReduceKeysToLongTask<K,V> rights, nextRight; MapReduceKeysToLongTask
	 * (BulkTask<K,V,?> p, int b, int i, int f, Node<K,V>[] t,
	 * MapReduceKeysToLongTask<K,V> nextRight, ToLongFunction<? super K>
	 * transformer, long basis, LongBinaryOperator reducer) { super(p, b, i, f,
	 * t); this.nextRight = nextRight; this.transformer = transformer;
	 * this.basis = basis; this.reducer = reducer; } public final Long
	 * getRawResult() { return result; } public final void compute() { final
	 * ToLongFunction<? super K> transformer; final LongBinaryOperator reducer;
	 * if ((transformer = this.transformer) != null && (reducer = this.reducer)
	 * != null) { long r = this.basis; for (int i = baseIndex, f, h; batch > 0
	 * && (h = ((f = baseLimit) + i) >>> 1) > i;) { addToPendingCount(1);
	 * (rights = new MapReduceKeysToLongTask<K,V> (this, batch >>>= 1, baseLimit
	 * = h, f, tab, rights, transformer, r, reducer)).fork(); } for (Node<K,V>
	 * p; (p = advance()) != null; ) r = reducer.applyAsLong(r,
	 * transformer.applyAsLong(p.key)); result = r; CountedCompleter<?> c; for
	 * (c = firstComplete(); c != null; c = c.nextComplete()) {
	 *
	 * @SuppressWarnings("unchecked") MapReduceKeysToLongTask<K,V> t =
	 * (MapReduceKeysToLongTask<K,V>)c, s = t.rights; while (s != null) {
	 * t.result = reducer.applyAsLong(t.result, s.result); s = t.rights =
	 * s.nextRight; } } } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class
	 * MapReduceValuesToLongTask<K,V> extends BulkTask<K,V,Long> { final
	 * ToLongFunction<? super V> transformer; final LongBinaryOperator reducer;
	 * final long basis; long result; MapReduceValuesToLongTask<K,V> rights,
	 * nextRight; MapReduceValuesToLongTask (BulkTask<K,V,?> p, int b, int i,
	 * int f, Node<K,V>[] t, MapReduceValuesToLongTask<K,V> nextRight,
	 * ToLongFunction<? super V> transformer, long basis, LongBinaryOperator
	 * reducer) { super(p, b, i, f, t); this.nextRight = nextRight;
	 * this.transformer = transformer; this.basis = basis; this.reducer =
	 * reducer; } public final Long getRawResult() { return result; } public
	 * final void compute() { final ToLongFunction<? super V> transformer; final
	 * LongBinaryOperator reducer; if ((transformer = this.transformer) != null
	 * && (reducer = this.reducer) != null) { long r = this.basis; for (int i =
	 * baseIndex, f, h; batch > 0 && (h = ((f = baseLimit) + i) >>> 1) > i;) {
	 * addToPendingCount(1); (rights = new MapReduceValuesToLongTask<K,V> (this,
	 * batch >>>= 1, baseLimit = h, f, tab, rights, transformer, r,
	 * reducer)).fork(); } for (Node<K,V> p; (p = advance()) != null; ) r =
	 * reducer.applyAsLong(r, transformer.applyAsLong(p.val)); result = r;
	 * CountedCompleter<?> c; for (c = firstComplete(); c != null; c =
	 * c.nextComplete()) {
	 *
	 * @SuppressWarnings("unchecked") MapReduceValuesToLongTask<K,V> t =
	 * (MapReduceValuesToLongTask<K,V>)c, s = t.rights; while (s != null) {
	 * t.result = reducer.applyAsLong(t.result, s.result); s = t.rights =
	 * s.nextRight; } } } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class MapReduceEntriesToLongTask
	 * extends BulkTask<Long> { final ToLongFunction<Map.Entry transformer;
	 * final LongBinaryOperator reducer; final long basis; long result;
	 * MapReduceEntriesToLongTask rights, nextRight; MapReduceEntriesToLongTask
	 * (BulkTask<?> p, int b, int i, int f, Node[] t, MapReduceEntriesToLongTask
	 * nextRight, ToLongFunction<Map.Entry> transformer, long basis,
	 * LongBinaryOperator reducer) { super(p, b, i, f, t); this.nextRight =
	 * nextRight; this.transformer = transformer; this.basis = basis;
	 * this.reducer = reducer; } public final Long getRawResult() { return
	 * result; } public final void compute() { final ToLongFunction<Map.Entry>
	 * transformer; final LongBinaryOperator reducer; if ((transformer =
	 * this.transformer) != null && (reducer = this.reducer) != null) { long r =
	 * this.basis; for (int i = baseIndex, f, h; batch > 0 && (h = ((f =
	 * baseLimit) + i) >>> 1) > i;) { addToPendingCount(1); (rights = new
	 * MapReduceEntriesToLongTask (this, batch >>>= 1, baseLimit = h, f, tab,
	 * rights, transformer, r, reducer)).fork(); } for (Node p; (p = advance())
	 * != null; ) r = reducer.applyAsLong(r, transformer.applyAsLong(p)); result
	 * = r; CountedCompleter<?> c; for (c = firstComplete(); c != null; c =
	 * c.nextComplete()) {
	 *
	 * @SuppressWarnings("unchecked") MapReduceEntriesToLongTask t =
	 * (MapReduceEntriesToLongTask)c, s = t.rights; while (s != null) { t.result
	 * = reducer.applyAsLong(t.result, s.result); s = t.rights = s.nextRight; }
	 * } } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class
	 * MapReduceMappingsToLongTask extends BulkTask<K,V,Long> { final
	 * ToLongBiFunction<? super K, ? super V> transformer; final
	 * LongBinaryOperator reducer; final long basis; long result;
	 * MapReduceMappingsToLongTask<K,V> rights, nextRight;
	 * MapReduceMappingsToLongTask (BulkTask<K,V,?> p, int b, int i, int f,
	 * Node<K,V>[] t, MapReduceMappingsToLongTask<K,V> nextRight,
	 * ToLongBiFunction<? super K, ? super V> transformer, long basis,
	 * LongBinaryOperator reducer) { super(p, b, i, f, t); this.nextRight =
	 * nextRight; this.transformer = transformer; this.basis = basis;
	 * this.reducer = reducer; } public final Long getRawResult() { return
	 * result; } public final void compute() { final ToLongBiFunction<? super K,
	 * ? super V> transformer; final LongBinaryOperator reducer; if
	 * ((transformer = this.transformer) != null && (reducer = this.reducer) !=
	 * null) { long r = this.basis; for (int i = baseIndex, f, h; batch > 0 &&
	 * (h = ((f = baseLimit) + i) >>> 1) > i;) { addToPendingCount(1); (rights =
	 * new MapReduceMappingsToLongTask<K,V> (this, batch >>>= 1, baseLimit = h,
	 * f, tab, rights, transformer, r, reducer)).fork(); } for (Node<K,V> p; (p
	 * = advance()) != null; ) r = reducer.applyAsLong(r,
	 * transformer.applyAsLong(p.key, p.val)); result = r; CountedCompleter<?>
	 * c; for (c = firstComplete(); c != null; c = c.nextComplete()) {
	 *
	 * @SuppressWarnings("unchecked") MapReduceMappingsToLongTask<K,V> t =
	 * (MapReduceMappingsToLongTask<K,V>)c, s = t.rights; while (s != null) {
	 * t.result = reducer.applyAsLong(t.result, s.result); s = t.rights =
	 * s.nextRight; } } } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class
	 * MapReduceKeysToIntTask<K,V> extends BulkTask<K,V,Integer> { final
	 * ToIntFunction<? super K> transformer; final IntBinaryOperator reducer;
	 * final int basis; int result; MapReduceKeysToIntTask<K,V> rights,
	 * nextRight; MapReduceKeysToIntTask (BulkTask<K,V,?> p, int b, int i, int
	 * f, Node<K,V>[] t, MapReduceKeysToIntTask<K,V> nextRight, ToIntFunction<?
	 * super K> transformer, int basis, IntBinaryOperator reducer) { super(p, b,
	 * i, f, t); this.nextRight = nextRight; this.transformer = transformer;
	 * this.basis = basis; this.reducer = reducer; } public final Integer
	 * getRawResult() { return result; } public final void compute() { final
	 * ToIntFunction<? super K> transformer; final IntBinaryOperator reducer; if
	 * ((transformer = this.transformer) != null && (reducer = this.reducer) !=
	 * null) { int r = this.basis; for (int i = baseIndex, f, h; batch > 0 && (h
	 * = ((f = baseLimit) + i) >>> 1) > i;) { addToPendingCount(1); (rights =
	 * new MapReduceKeysToIntTask<K,V> (this, batch >>>= 1, baseLimit = h, f,
	 * tab, rights, transformer, r, reducer)).fork(); } for (Node<K,V> p; (p =
	 * advance()) != null; ) r = reducer.applyAsInt(r,
	 * transformer.applyAsInt(p.key)); result = r; CountedCompleter<?> c; for (c
	 * = firstComplete(); c != null; c = c.nextComplete()) {
	 *
	 * @SuppressWarnings("unchecked") MapReduceKeysToIntTask<K,V> t =
	 * (MapReduceKeysToIntTask<K,V>)c, s = t.rights; while (s != null) {
	 * t.result = reducer.applyAsInt(t.result, s.result); s = t.rights =
	 * s.nextRight; } } } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class
	 * MapReduceValuesToIntTask<K,V> extends BulkTask<K,V,Integer> { final
	 * ToIntFunction<? super V> transformer; final IntBinaryOperator reducer;
	 * final int basis; int result; MapReduceValuesToIntTask<K,V> rights,
	 * nextRight; MapReduceValuesToIntTask (BulkTask<K,V,?> p, int b, int i, int
	 * f, Node<K,V>[] t, MapReduceValuesToIntTask<K,V> nextRight,
	 * ToIntFunction<? super V> transformer, int basis, IntBinaryOperator
	 * reducer) { super(p, b, i, f, t); this.nextRight = nextRight;
	 * this.transformer = transformer; this.basis = basis; this.reducer =
	 * reducer; } public final Integer getRawResult() { return result; } public
	 * final void compute() { final ToIntFunction<? super V> transformer; final
	 * IntBinaryOperator reducer; if ((transformer = this.transformer) != null
	 * && (reducer = this.reducer) != null) { int r = this.basis; for (int i =
	 * baseIndex, f, h; batch > 0 && (h = ((f = baseLimit) + i) >>> 1) > i;) {
	 * addToPendingCount(1); (rights = new MapReduceValuesToIntTask<K,V> (this,
	 * batch >>>= 1, baseLimit = h, f, tab, rights, transformer, r,
	 * reducer)).fork(); } for (Node<K,V> p; (p = advance()) != null; ) r =
	 * reducer.applyAsInt(r, transformer.applyAsInt(p.val)); result = r;
	 * CountedCompleter<?> c; for (c = firstComplete(); c != null; c =
	 * c.nextComplete()) {
	 *
	 * @SuppressWarnings("unchecked") MapReduceValuesToIntTask<K,V> t =
	 * (MapReduceValuesToIntTask<K,V>)c, s = t.rights; while (s != null) {
	 * t.result = reducer.applyAsInt(t.result, s.result); s = t.rights =
	 * s.nextRight; } } } } }
	 */
	/*
	 * @SuppressWarnings("serial") static final class MapReduceEntriesToIntTask
	 * extends BulkTask<Integer> { final ToIntFunction<Map.Entry> transformer;
	 * final IntBinaryOperator reducer; final int basis; int result;
	 * MapReduceEntriesToIntTask rights, nextRight; MapReduceEntriesToIntTask
	 * (BulkTask<?> p, int b, int i, int f, Node[] t, MapReduceEntriesToIntTask
	 * nextRight, ToIntFunction<Map.Entry> transformer, int basis,
	 * IntBinaryOperator reducer) { super(p, b, i, f, t); this.nextRight =
	 * nextRight; this.transformer = transformer; this.basis = basis;
	 * this.reducer = reducer; } public final Integer getRawResult() { return
	 * result; } public final void compute() { final ToIntFunction<Map.Entry>
	 * transformer; final IntBinaryOperator reducer; if ((transformer =
	 * this.transformer) != null && (reducer = this.reducer) != null) { int r =
	 * this.basis; for (int i = baseIndex, f, h; batch > 0 && (h = ((f =
	 * baseLimit) + i) >>> 1) > i;) { addToPendingCount(1); (rights = new
	 * MapReduceEntriesToIntTask (this, batch >>>= 1, baseLimit = h, f, tab,
	 * rights, transformer, r, reducer)).fork(); } for (Node p; (p = advance())
	 * != null; ) r = reducer.applyAsInt(r, transformer.applyAsInt(p)); result =
	 * r; CountedCompleter<?> c; for (c = firstComplete(); c != null; c =
	 * c.nextComplete()) {
	 *
	 * @SuppressWarnings("unchecked") MapReduceEntriesToIntTask t =
	 * (MapReduceEntriesToIntTask)c, s = t.rights; while (s != null) { t.result
	 * = reducer.applyAsInt(t.result, s.result); s = t.rights = s.nextRight; } }
	 * } } }
	 */

	/*
	 * @SuppressWarnings("serial") static final class
	 * MapReduceMappingsToIntTask<K,V> extends BulkTask<K,V,Integer> { final
	 * ToIntBiFunction<? super K, ? super V> transformer; final
	 * IntBinaryOperator reducer; final int basis; int result;
	 * MapReduceMappingsToIntTask<K,V> rights, nextRight;
	 * MapReduceMappingsToIntTask (BulkTask<K,V,?> p, int b, int i, int f,
	 * Node<K,V>[] t, MapReduceMappingsToIntTask<K,V> nextRight,
	 * ToIntBiFunction<? super K, ? super V> transformer, int basis,
	 * IntBinaryOperator reducer) { super(p, b, i, f, t); this.nextRight =
	 * nextRight; this.transformer = transformer; this.basis = basis;
	 * this.reducer = reducer; } public final Integer getRawResult() { return
	 * result; } public final void compute() { final ToIntBiFunction<? super K,
	 * ? super V> transformer; final IntBinaryOperator reducer; if ((transformer
	 * = this.transformer) != null && (reducer = this.reducer) != null) { int r
	 * = this.basis; for (int i = baseIndex, f, h; batch > 0 && (h = ((f =
	 * baseLimit) + i) >>> 1) > i;) { addToPendingCount(1); (rights = new
	 * MapReduceMappingsToIntTask<K,V> (this, batch >>>= 1, baseLimit = h, f,
	 * tab, rights, transformer, r, reducer)).fork(); } for (Node<K,V> p; (p =
	 * advance()) != null; ) r = reducer.applyAsInt(r,
	 * transformer.applyAsInt(p.key, p.val)); result = r; CountedCompleter<?> c;
	 * for (c = firstComplete(); c != null; c = c.nextComplete()) {
	 *
	 * @SuppressWarnings("unchecked") MapReduceMappingsToIntTask<K,V> t =
	 * (MapReduceMappingsToIntTask<K,V>)c, s = t.rights; while (s != null) {
	 * t.result = reducer.applyAsInt(t.result, s.result); s = t.rights =
	 * s.nextRight; } } } } }
	 */

	// Unsafe mechanics
	private static final sun.misc.Unsafe U;

	/* ---------------- Static utilities -------------- */

	private static final long SIZECTL;

	private static final long TRANSFERINDEX;

	private static final long BASECOUNT;

	private static final long CELLSBUSY;

	/* ---------------- Table element access -------------- */

	/*
	 * Volatile access methods are used for table elements as well as elements
	 * of in-progress next table while resizing. All uses of the tab arguments
	 * must be null checked by callers. All callers also paranoically precheck
	 * that tab's length is not zero (or an equivalent check), thus ensuring
	 * that any index argument taking the form of a hash value anded with
	 * (length - 1) is a valid index. Note that, to be correct wrt arbitrary
	 * concurrency errors by users, these checks must operate on local
	 * variables, which accounts for some odd-looking inline assignments below.
	 * Note that calls to setTabAt always occur within locked regions, and so in
	 * principle require only release ordering, not full volatile semantics, but
	 * are currently coded as volatile writes to be conservative.
	 */

	private static final long CELLVALUE;

	private static final long ABASE;

	private static final int ASHIFT;

	/* ---------------- Fields -------------- */

	static
	{
		try
		{
			U = getUnsafe();
			Class<?> k = InternalConcurrentHashMap.class;
			SIZECTL = U.objectFieldOffset(k.getDeclaredField("sizeCtl"));
			TRANSFERINDEX = U.objectFieldOffset(k.getDeclaredField("transferIndex"));
			BASECOUNT = U.objectFieldOffset(k.getDeclaredField("baseCount"));
			CELLSBUSY = U.objectFieldOffset(k.getDeclaredField("cellsBusy"));
			Class<?> ck = CounterCell.class;
			CELLVALUE = U.objectFieldOffset(ck.getDeclaredField("value"));
			Class<?> ak = Node[].class;
			ABASE = U.arrayBaseOffset(ak);
			int scale = U.arrayIndexScale(ak);
			if ((scale & (scale - 1)) != 0)
			{
				throw new Error("data type scale not a power of two");
			}
			ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
		}
		catch (Exception e)
		{
			throw new Error(e);
		}
	}

	/**
	 * The array of bins. Lazily initialized upon first insertion. Size is
	 * always a power of two. Accessed directly by iterators.
	 */
	transient volatile Node[] table;

	/**
	 * The next table to use; non-null only while resizing.
	 */
	private transient volatile Node[] nextTable;

	/**
	 * Base counter value, used mainly when there is no contention, but also as
	 * a fallback during table initialization races. Updated via CAS.
	 */
	private transient volatile long baseCount;

	/**
	 * Table initialization and resizing control. When negative, the table is
	 * being initialized or resized: -1 for initialization, else -(1 + the
	 * number of active resizing threads). Otherwise, when table is null, holds
	 * the initial table size to use upon creation, or 0 for default. After
	 * initialization, holds the next element count value upon which to resize
	 * the table.
	 */
	private transient volatile int sizeCtl;

	/**
	 * The next table index (plus one) to split while resizing.
	 */
	private transient volatile int transferIndex;

	/**
	 * Spinlock (locked via CAS) used when resizing and/or creating
	 * CounterCells.
	 */
	private transient volatile int cellsBusy;

	/**
	 * Table of counter cells. When non-null, size is a power of 2.
	 */
	private transient volatile CounterCell[] counterCells;
	// views
	private transient KeySetView keySet;
	private transient ValuesView values;

	/* ---------------- Public operations -------------- */

	private transient EntrySetView entrySet;

	/**
	 * Creates a new, empty map with the default initial table size (16).
	 */
	public InternalConcurrentHashMap()
	{
	}

	/**
	 * Creates a new, empty map with an initial table size accommodating the
	 * specified number of elements without the need to dynamically resize.
	 *
	 * @param initialCapacity
	 *            The implementation performs internal sizing to accommodate
	 *            this many elements.
	 * @throws IllegalArgumentException
	 *             if the initial capacity of elements is negative
	 */
	public InternalConcurrentHashMap(int initialCapacity)
	{
		if (initialCapacity < 0)
		{
			throw new IllegalArgumentException();
		}
		int cap = ((initialCapacity >= (MAXIMUM_CAPACITY >>> 1)) ? MAXIMUM_CAPACITY : tableSizeFor(initialCapacity + (initialCapacity >>> 1) + 1));
		this.sizeCtl = cap;
	}

	/**
	 * Creates a new map with the same mappings as the given map.
	 *
	 * @param m
	 *            the map
	 */
	/*
	 * public InternalConcurrentHashMap(Map<? extends K, ? extends V> m) {
	 * this.sizeCtl = DEFAULT_CAPACITY; putAll(m); }
	 */
	/**
	 * Creates a new, empty map with an initial table size based on the given
	 * number of elements ({@code initialCapacity}) and initial table density (
	 * {@code loadFactor}).
	 *
	 * @param initialCapacity
	 *            the initial capacity. The implementation performs internal
	 *            sizing to accommodate this many elements, given the specified
	 *            load factor.
	 * @param loadFactor
	 *            the load factor (table density) for establishing the initial
	 *            table size
	 * @throws IllegalArgumentException
	 *             if the initial capacity of elements is negative or the load
	 *             factor is nonpositive
	 *
	 * @since 1.6
	 */
	public InternalConcurrentHashMap(int initialCapacity, float loadFactor)
	{
		this(initialCapacity, loadFactor, 1);
	}

	// Original (since JDK1.2) Map methods

	/**
	 * Creates a new, empty map with an initial table size based on the given
	 * number of elements ({@code initialCapacity}), table density (
	 * {@code loadFactor}), and number of concurrently updating threads (
	 * {@code concurrencyLevel}).
	 *
	 * @param initialCapacity
	 *            the initial capacity. The implementation performs internal
	 *            sizing to accommodate this many elements, given the specified
	 *            load factor.
	 * @param loadFactor
	 *            the load factor (table density) for establishing the initial
	 *            table size
	 * @param concurrencyLevel
	 *            the estimated number of concurrently updating threads. The
	 *            implementation may use this value as a sizing hint.
	 * @throws IllegalArgumentException
	 *             if the initial capacity is negative or the load factor or
	 *             concurrencyLevel are nonpositive
	 */
	public InternalConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel)
	{
		if (!(loadFactor > 0.0f) || initialCapacity < 0 || concurrencyLevel <= 0)
		{
			throw new IllegalArgumentException();
		}
		if (initialCapacity < concurrencyLevel)
		{
			initialCapacity = concurrencyLevel; // as estimated threads
		}
		long size = (long)(1.0 + initialCapacity / loadFactor);
		int cap = (size >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : tableSizeFor((int)size);
		this.sizeCtl = cap;
	}

	public static sun.misc.Unsafe getUnsafe()
	{
		try
		{
			final Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			return (sun.misc.Unsafe)f.get(null);
		}
		catch (final Exception e)
		{
			return null;
		}
	}

	/**
	 * Returns a power of two table size for the given desired capacity. See
	 * Hackers Delight, sec 3.2
	 */
	private static final int tableSizeFor(int c)
	{
		int n = c - 1;
		n |= n >>> 1;
		n |= n >>> 2;
		n |= n >>> 4;
		n |= n >>> 8;
		n |= n >>> 16;
		return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
	}

	static final boolean casTabAt(Node[] tab, int i, Node c, Node v)
	{
		return U.compareAndSwapObject(tab, ((long)i << ASHIFT) + ABASE, c, v);
	}

	/**
	 * Returns x's Class if it is of the form "class C implements
	 * Comparable<C>", else null.
	 */
	static Class<?> comparableClassFor(Object x)
	{
		if (x instanceof Comparable)
		{
			Class<?> c;
			Type[] ts, as;
			Type t;
			ParameterizedType p;
			if ((c = x.getClass()) == String.class)
			{
				return c;
			}
			if ((ts = c.getGenericInterfaces()) != null)
			{
				for (Type element : ts)
				{
					if (((t = element) instanceof ParameterizedType) && ((p = (ParameterizedType)t).getRawType() == Comparable.class) && (as = p.getActualTypeArguments()) != null && as.length == 1 && as[0] == c)
					{
						return c;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Returns k.compareTo(x) if x matches kc (k's screened comparable class),
	 * else 0.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	// for cast to Comparable
	static int compareComparables(Class<?> kc, Object k, Object x)
	{
		return (x == null || x.getClass() != kc ? 0 : ((Comparable)k).compareTo(x));
	}

	/**
	 * Returns the stamp bits for resizing a table of size n. Must be negative
	 * when shifted left by RESIZE_STAMP_SHIFT.
	 */
	static final int resizeStamp(int n)
	{
		return Integer.numberOfLeadingZeros(n) | (1 << (RESIZE_STAMP_BITS - 1));
	}

	/**
	 * Copies all of the mappings from the specified map to this one. These
	 * mappings replace any mappings that this map had for any of the keys
	 * currently in the specified map.
	 *
	 * @param m
	 *            mappings to be stored in this map
	 */
	/*
	 * public void putAll(Map<? extends K, ? extends V> m) {
	 * tryPresize(m.size()); for (Map.Entry<? extends K, ? extends V> e :
	 * m.entrySet()) putVal(e.getKey(), e.getValue(), false); }
	 */

	static final void setTabAt(Node[] tab, int i, Node v)
	{
		U.putObjectVolatile(tab, ((long)i << ASHIFT) + ABASE, v);
	}

	/**
	 * Spreads (XORs) higher bits of hash to lower and also forces top bit to 0.
	 * Because the table uses power-of-two masking, sets of hashes that vary
	 * only in bits above the current mask will always collide. (Among known
	 * examples are sets of Float keys holding consecutive whole numbers in
	 * small tables.) So we apply a transform that spreads the impact of higher
	 * bits downward. There is a tradeoff between speed, utility, and quality of
	 * bit-spreading. Because many common sets of hashes are already reasonably
	 * distributed (so don't benefit from spreading), and because we use trees
	 * to handle large sets of collisions in bins, we just XOR some shifted bits
	 * in the cheapest possible way to reduce systematic lossage, as well as to
	 * incorporate impact of the highest bits that would otherwise never be used
	 * in index calculations because of table bounds.
	 */
	static final int spread(int h)
	{
		return (h ^ (h >>> 16)) & HASH_BITS;
	}

	@SuppressWarnings("unchecked")
	static final Node tabAt(Node[] tab, int i)
	{
		return (Node)U.getObjectVolatile(tab, ((long)i << ASHIFT) + ABASE);
	}

	/**
	 * Returns a list on non-TreeNodes replacing those in given list.
	 */
	static Node untreeify(Node b)
	{
		Node hd = null, tl = null;
		for (Node q = b; q != null; q = q.next)
		{
			Node p = new Node(q.hash, q.key, q.val, null);
			if (tl == null)
			{
				hd = p;
			}
			else
			{
				tl.next = p;
			}
			tl = p;
		}
		return hd;
	}

	/**
	 * Removes all of the mappings from this map.
	 */
	public void clear()
	{
		// try
		// {
		long delta = 0L; // negative number of deletions
		int i = 0;
		Node[] tab = table;
		while (tab != null && i < tab.length)
		{
			int fh;
			Node f = tabAt(tab, i);
			if (f == null)
			{
				++i;
			}
			else if ((fh = f.hash) == MOVED)
			{
				tab = helpTransfer(tab, f);
				i = 0; // restart
			}
			else
			{
				synchronized (f)
				{
					if (tabAt(tab, i) == f)
					{
						Node p = (fh >= 0 ? f : (f instanceof TreeBin) ? ((TreeBin)f).first : null);
						while (p != null)
						{
							--delta;
							p = p.next;
						}
						setTabAt(tab, i++, null);
					}
				}
			}
		}
		if (delta != 0L)
		{
			addCount(delta, -1);
			// }
			// catch(Throwable e)
			// {
			// HRDBMSWorker.logger.debug("", e);
			// }
		}
	}

	/**
	 * Legacy method testing if some key maps into the specified value in this
	 * table. This method is identical in functionality to
	 * {@link #containsValue(Object)}, and exists solely to ensure full
	 * compatibility with class {@link java.util.Hashtable}, which supported
	 * this method prior to introduction of the Java Collections framework.
	 *
	 * @param value
	 *            a value to search for
	 * @return {@code true} if and only if some key maps to the {@code value}
	 *         argument in this table as determined by the {@code equals}
	 *         method; {@code false} otherwise
	 * @throws NullPointerException
	 *             if the specified value is null
	 */
	public boolean contains(Object value)
	{
		return containsValue(value);
	}

	/**
	 * Tests if the specified object is a key in this table.
	 *
	 * @param key
	 *            possible key
	 * @return {@code true} if and only if the specified object is a key in this
	 *         table, as determined by the {@code equals} method; {@code false}
	 *         otherwise
	 * @throws NullPointerException
	 *             if the specified key is null
	 */
	public boolean containsKey(long key)
	{
		return get(key) != null;
	}

	/**
	 * Returns {@code true} if this map maps one or more keys to the specified
	 * value. Note: This method may require a full traversal of the map, and is
	 * much slower than method {@code containsKey}.
	 *
	 * @param value
	 *            value whose presence in this map is to be tested
	 * @return {@code true} if this map maps one or more keys to the specified
	 *         value
	 * @throws NullPointerException
	 *             if the specified value is null
	 */
	public boolean containsValue(Object value)
	{
		if (value == null)
		{
			throw new NullPointerException();
		}
		Node[] t;
		if ((t = table) != null)
		{
			Traverser it = new Traverser(t, t.length, 0, t.length);
			for (Node p; (p = it.advance()) != null;)
			{
				ArrayList<Object> v;
				if ((v = p.val) == value || (v != null && value.equals(v)))
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Returns an enumeration of the values in this table.
	 *
	 * @return an enumeration of the values in this table
	 * @see #values()
	 */
	public Enumeration<ArrayList<Object>> elements()
	{
		Node[] t;
		int f = (t = table) == null ? 0 : t.length;
		return new ValueIterator(t, f, 0, f, this);
	}

	/**
	 * Returns a {@link Set} view of the mappings contained in this map. The set
	 * is backed by the map, so changes to the map are reflected in the set, and
	 * vice-versa. The set supports element removal, which removes the
	 * corresponding mapping from the map, via the {@code Iterator.remove},
	 * {@code Set.remove}, {@code removeAll}, {@code retainAll}, and
	 * {@code clear} operations.
	 *
	 * <p>
	 * The view's iterators and spliterators are <a
	 * href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
	 *
	 * <p>
	 * The view's {@code spliterator} reports {@link Spliterator#CONCURRENT},
	 * {@link Spliterator#DISTINCT}, and {@link Spliterator#NONNULL}.
	 *
	 * @return the set view
	 */
	public EntrySetView entrySet()
	{
		// try
		// {
		EntrySetView es;
		return (es = entrySet) != null ? es : (entrySet = new EntrySetView(this));
		// }
		// catch(Throwable e)
		// {
		// HRDBMSWorker.logger.debug("", e);
		// return null;
		// }
	}

	/**
	 * Compares the specified object with this map for equality. Returns
	 * {@code true} if the given object is a map with the same mappings as this
	 * map. This operation may return misleading results if either map is
	 * concurrently modified during execution of this method.
	 *
	 * @param o
	 *            object to be compared for equality with this map
	 * @return {@code true} if the specified object is equal to this map
	 */
	@Override
	public boolean equals(Object o)
	{
		if (o != this)
		{
			if (!(o instanceof InternalConcurrentHashMap))
			{
				return false;
			}
			InternalConcurrentHashMap m = (InternalConcurrentHashMap)o;
			Node[] t;
			int f = (t = table) == null ? 0 : t.length;
			Traverser it = new Traverser(t, f, 0, f);
			for (Node p; (p = it.advance()) != null;)
			{
				ArrayList<Object> val = p.val;
				Object v = m.get(p.key);
				if (v == null || (v != val && !v.equals(val)))
				{
					return false;
				}
			}
			EntryIterator iter = m.entrySet().iterator();
			while (iter.hasNext())
			{
				MapEntry e = iter.next();
				long mk;
				ArrayList<Object> mv, v;
				if ((mk = e.getKey()) == -1 || (mv = e.getValue()) == null || (v = get(mk)) == null || (mv != v && !mv.equals(v)))
				{
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Reconstitutes the instance from a stream (that is, deserializes it).
	 *
	 * @param s
	 *            the stream
	 * @throws ClassNotFoundException
	 *             if the class of a serialized object could not be found
	 * @throws java.io.IOException
	 *             if an I/O error occurs
	 */
	/*
	 * private void readObject(java.io.ObjectInputStream s) throws
	 * java.io.IOException, ClassNotFoundException { /* To improve performance
	 * in typical cases, we create nodes while reading, then place in table once
	 * size is known. However, we must also validate uniqueness and deal with
	 * overpopulated bins while doing so, which requires specialized versions of
	 * putVal mechanics.
	 */
	/*
	 * sizeCtl = -1; // force exclusion for table construction
	 * s.defaultReadObject(); long size = 0L; Node p = null; for (;;) {
	 *
	 * @SuppressWarnings("unchecked") K k = (K) s.readObject();
	 *
	 * @SuppressWarnings("unchecked") V v = (V) s.readObject(); if (k != null &&
	 * v != null) { p = new Node<K,V>(spread(k.hashCode()), k, v, p); ++size; }
	 * else break; } if (size == 0L) sizeCtl = 0; else { int n; if (size >=
	 * (long)(MAXIMUM_CAPACITY >>> 1)) n = MAXIMUM_CAPACITY; else { int sz =
	 * (int)size; n = tableSizeFor(sz + (sz >>> 1) + 1); }
	 *
	 * @SuppressWarnings("unchecked") Node<K,V>[] tab = (Node<K,V>[])new
	 * Node<?,?>[n]; int mask = n - 1; long added = 0L; while (p != null) {
	 * boolean insertAtFront; Node<K,V> next = p.next, first; int h = p.hash, j
	 * = h & mask; if ((first = tabAt(tab, j)) == null) insertAtFront = true;
	 * else { K k = p.key; if (first.hash < 0) { TreeBin<K,V> t =
	 * (TreeBin<K,V>)first; if (t.putTreeVal(h, k, p.val) == null) ++added;
	 * insertAtFront = false; } else { int binCount = 0; insertAtFront = true;
	 * Node<K,V> q; K qk; for (q = first; q != null; q = q.next) { if (q.hash ==
	 * h && ((qk = q.key) == k || (qk != null && k.equals(qk)))) { insertAtFront
	 * = false; break; } ++binCount; } if (insertAtFront && binCount >=
	 * TREEIFY_THRESHOLD) { insertAtFront = false; ++added; p.next = first;
	 * TreeNode<K,V> hd = null, tl = null; for (q = p; q != null; q = q.next) {
	 * TreeNode<K,V> t = new TreeNode<K,V> (q.hash, q.key, q.val, null, null);
	 * if ((t.prev = tl) == null) hd = t; else tl.next = t; tl = t; }
	 * setTabAt(tab, j, new TreeBin<K,V>(hd)); } } } if (insertAtFront) {
	 * ++added; p.next = first; setTabAt(tab, j, p); } p = next; } table = tab;
	 * sizeCtl = n - (n >>> 2); baseCount = added; } }
	 */

	// ConcurrentMap methods

	/**
	 * Returns the value to which the specified key is mapped, or {@code null}
	 * if this map contains no mapping for the key.
	 *
	 * <p>
	 * More formally, if this map contains a mapping from a key {@code k} to a
	 * value {@code v} such that {@code key.equals(k)}, then this method returns
	 * {@code v}; otherwise it returns {@code null}. (There can be at most one
	 * such mapping.)
	 *
	 * @throws NullPointerException
	 *             if the specified key is null
	 */
	public ArrayList<Object> get(long key)
	{
		// try
		// {
		Node[] tab;
		Node e, p;
		int n, eh;
		int h = spread((int)(key ^ (key >>> 32)));
		if ((tab = table) != null && (n = tab.length) > 0 && (e = tabAt(tab, (n - 1) & h)) != null)
		{
			if ((eh = e.hash) == h)
			{
				if ((e.key) == key)
				{
					return e.val;
				}
			}
			else if (eh < 0)
			{
				return (p = e.find(h, key)) != null ? p.val : null;
			}
			while ((e = e.next) != null)
			{
				if (e.hash == h && ((e.key) == key))
				{
					return e.val;
				}
			}
		}
		return null;
		// }
		// catch(Throwable e)
		// {
		// HRDBMSWorker.logger.debug("", e);
		// return null;
		// }
	}

	/**
	 * Returns the value to which the specified key is mapped, or the given
	 * default value if this map contains no mapping for the key.
	 *
	 * @param key
	 *            the key whose associated value is to be returned
	 * @param defaultValue
	 *            the value to return if this map contains no mapping for the
	 *            given key
	 * @return the mapping for the key, if present; else the default value
	 * @throws NullPointerException
	 *             if the specified key is null
	 */
	public ArrayList<Object> getOrDefault(long key, ArrayList<Object> defaultValue)
	{
		ArrayList<Object> v;
		return (v = get(key)) == null ? defaultValue : v;
	}

	/**
	 * Returns the hash code value for this {@link Map}, i.e., the sum of, for
	 * each key-value pair in the map, {@code key.hashCode() ^ value.hashCode()}
	 * .
	 *
	 * @return the hash code value for this map
	 */
	@Override
	public int hashCode()
	{
		int h = 0;
		Node[] t;
		if ((t = table) != null)
		{
			Traverser it = new Traverser(t, t.length, 0, t.length);
			for (Node p; (p = it.advance()) != null;)
			{
				h += ((int)(p.key ^ (p.key >>> 32))) ^ p.val.hashCode();
			}
		}
		return h;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isEmpty()
	{
		return sumCount() <= 0L; // ignore transient negative values
	}

	// Overrides of JDK8+ Map extension method defaults

	/**
	 * Returns a {@link Set} view of the keys contained in this map. The set is
	 * backed by the map, so changes to the map are reflected in the set, and
	 * vice-versa. The set supports element removal, which removes the
	 * corresponding mapping from this map, via the {@code Iterator.remove},
	 * {@code Set.remove}, {@code removeAll}, {@code retainAll}, and
	 * {@code clear} operations. It does not support the {@code add} or
	 * {@code addAll} operations.
	 *
	 * <p>
	 * The view's iterators and spliterators are <a
	 * href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
	 *
	 * <p>
	 * The view's {@code spliterator} reports {@link Spliterator#CONCURRENT},
	 * {@link Spliterator#DISTINCT}, and {@link Spliterator#NONNULL}.
	 *
	 * @return the set view
	 */
	public KeySetView keySet()
	{
		KeySetView ks;
		return (ks = keySet) != null ? ks : (keySet = new KeySetView(this, null));
	}

	/*
	 * public void forEach(BiConsumer<? super K, ? super V> action) { if (action
	 * == null) throw new NullPointerException(); Node<K,V>[] t; if ((t = table)
	 * != null) { Traverser<K,V> it = new Traverser<K,V>(t, t.length, 0,
	 * t.length); for (Node<K,V> p; (p = it.advance()) != null; ) {
	 * action.accept(p.key, p.val); } } }
	 *
	 * public void replaceAll(BiFunction<? super K, ? super V, ? extends V>
	 * function) { if (function == null) throw new NullPointerException();
	 * Node<K,V>[] t; if ((t = table) != null) { Traverser<K,V> it = new
	 * Traverser<K,V>(t, t.length, 0, t.length); for (Node<K,V> p; (p =
	 * it.advance()) != null; ) { V oldValue = p.val; for (K key = p.key;;) { V
	 * newValue = function.apply(key, oldValue); if (newValue == null) throw new
	 * NullPointerException(); if (replaceNode(key, newValue, oldValue) != null
	 * || (oldValue = get(key)) == null) break; } } } }
	 */

	/**
	 * If the specified key is not already associated with a value, attempts to
	 * compute its value using the given mapping function and enters it into
	 * this map unless {@code null}. The entire method invocation is performed
	 * atomically, so the function is applied at most once per key. Some
	 * attempted update operations on this map by other threads may be blocked
	 * while computation is in progress, so the computation should be short and
	 * simple, and must not attempt to update any other mappings of this map.
	 *
	 * @param key
	 *            key with which the specified value is to be associated
	 * @param mappingFunction
	 *            the function to compute a value
	 * @return the current (existing or computed) value associated with the
	 *         specified key, or null if the computed value is null
	 * @throws NullPointerException
	 *             if the specified key or mappingFunction is null
	 * @throws IllegalStateException
	 *             if the computation detectably attempts a recursive update to
	 *             this map that would otherwise never complete
	 * @throws RuntimeException
	 *             or Error if the mappingFunction does so, in which case the
	 *             mapping is left unestablished
	 */
	/*
	 * public V computeIfAbsent(K key, Function<? super K, ? extends V>
	 * mappingFunction) { if (key == null || mappingFunction == null) throw new
	 * NullPointerException(); int h = spread(key.hashCode()); V val = null; int
	 * binCount = 0; for (Node<K,V>[] tab = table;;) { Node<K,V> f; int n, i,
	 * fh; if (tab == null || (n = tab.length) == 0) tab = initTable(); else if
	 * ((f = tabAt(tab, i = (n - 1) & h)) == null) { Node<K,V> r = new
	 * ReservationNode<K,V>(); synchronized (r) { if (casTabAt(tab, i, null, r))
	 * { binCount = 1; Node<K,V> node = null; try { if ((val =
	 * mappingFunction.apply(key)) != null) node = new Node<K,V>(h, key, val,
	 * null); } finally { setTabAt(tab, i, node); } } } if (binCount != 0)
	 * break; } else if ((fh = f.hash) == MOVED) tab = helpTransfer(tab, f);
	 * else { boolean added = false; synchronized (f) { if (tabAt(tab, i) == f)
	 * { if (fh >= 0) { binCount = 1; for (Node<K,V> e = f;; ++binCount) { K ek;
	 * V ev; if (e.hash == h && ((ek = e.key) == key || (ek != null &&
	 * key.equals(ek)))) { val = e.val; break; } Node<K,V> pred = e; if ((e =
	 * e.next) == null) { if ((val = mappingFunction.apply(key)) != null) {
	 * added = true; pred.next = new Node<K,V>(h, key, val, null); } break; } }
	 * } else if (f instanceof TreeBin) { binCount = 2; TreeBin<K,V> t =
	 * (TreeBin<K,V>)f; TreeNode<K,V> r, p; if ((r = t.root) != null && (p =
	 * r.findTreeNode(h, key, null)) != null) val = p.val; else if ((val =
	 * mappingFunction.apply(key)) != null) { added = true; t.putTreeVal(h, key,
	 * val); } } } } if (binCount != 0) { if (binCount >= TREEIFY_THRESHOLD)
	 * treeifyBin(tab, i); if (!added) return val; break; } } } if (val != null)
	 * addCount(1L, binCount); return val; }
	 */
	/**
	 * If the value for the specified key is present, attempts to compute a new
	 * mapping given the key and its current mapped value. The entire method
	 * invocation is performed atomically. Some attempted update operations on
	 * this map by other threads may be blocked while computation is in
	 * progress, so the computation should be short and simple, and must not
	 * attempt to update any other mappings of this map.
	 *
	 * @param key
	 *            key with which a value may be associated
	 * @param remappingFunction
	 *            the function to compute a value
	 * @return the new value associated with the specified key, or null if none
	 * @throws NullPointerException
	 *             if the specified key or remappingFunction is null
	 * @throws IllegalStateException
	 *             if the computation detectably attempts a recursive update to
	 *             this map that would otherwise never complete
	 * @throws RuntimeException
	 *             or Error if the remappingFunction does so, in which case the
	 *             mapping is unchanged
	 */
	/*
	 * public V computeIfPresent(K key, BiFunction<? super K, ? super V, ?
	 * extends V> remappingFunction) { if (key == null || remappingFunction ==
	 * null) throw new NullPointerException(); int h = spread(key.hashCode()); V
	 * val = null; int delta = 0; int binCount = 0; for (Node<K,V>[] tab =
	 * table;;) { Node<K,V> f; int n, i, fh; if (tab == null || (n = tab.length)
	 * == 0) tab = initTable(); else if ((f = tabAt(tab, i = (n - 1) & h)) ==
	 * null) break; else if ((fh = f.hash) == MOVED) tab = helpTransfer(tab, f);
	 * else { synchronized (f) { if (tabAt(tab, i) == f) { if (fh >= 0) {
	 * binCount = 1; for (Node<K,V> e = f, pred = null;; ++binCount) { K ek; if
	 * (e.hash == h && ((ek = e.key) == key || (ek != null && key.equals(ek))))
	 * { val = remappingFunction.apply(key, e.val); if (val != null) e.val =
	 * val; else { delta = -1; Node<K,V> en = e.next; if (pred != null)
	 * pred.next = en; else setTabAt(tab, i, en); } break; } pred = e; if ((e =
	 * e.next) == null) break; } } else if (f instanceof TreeBin) { binCount =
	 * 2; TreeBin<K,V> t = (TreeBin<K,V>)f; TreeNode<K,V> r, p; if ((r = t.root)
	 * != null && (p = r.findTreeNode(h, key, null)) != null) { val =
	 * remappingFunction.apply(key, p.val); if (val != null) p.val = val; else {
	 * delta = -1; if (t.removeTreeNode(p)) setTabAt(tab, i,
	 * untreeify(t.first)); } } } } } if (binCount != 0) break; } } if (delta !=
	 * 0) addCount((long)delta, binCount); return val; }
	 */
	/**
	 * Attempts to compute a mapping for the specified key and its current
	 * mapped value (or {@code null} if there is no current mapping). The entire
	 * method invocation is performed atomically. Some attempted update
	 * operations on this map by other threads may be blocked while computation
	 * is in progress, so the computation should be short and simple, and must
	 * not attempt to update any other mappings of this Map.
	 *
	 * @param key
	 *            key with which the specified value is to be associated
	 * @param remappingFunction
	 *            the function to compute a value
	 * @return the new value associated with the specified key, or null if none
	 * @throws NullPointerException
	 *             if the specified key or remappingFunction is null
	 * @throws IllegalStateException
	 *             if the computation detectably attempts a recursive update to
	 *             this map that would otherwise never complete
	 * @throws RuntimeException
	 *             or Error if the remappingFunction does so, in which case the
	 *             mapping is unchanged
	 */
	/*
	 * public V compute(K key, BiFunction<? super K, ? super V, ? extends V>
	 * remappingFunction) { if (key == null || remappingFunction == null) throw
	 * new NullPointerException(); int h = spread(key.hashCode()); V val = null;
	 * int delta = 0; int binCount = 0; for (Node<K,V>[] tab = table;;) {
	 * Node<K,V> f; int n, i, fh; if (tab == null || (n = tab.length) == 0) tab
	 * = initTable(); else if ((f = tabAt(tab, i = (n - 1) & h)) == null) {
	 * Node<K,V> r = new ReservationNode<K,V>(); synchronized (r) { if
	 * (casTabAt(tab, i, null, r)) { binCount = 1; Node<K,V> node = null; try {
	 * if ((val = remappingFunction.apply(key, null)) != null) { delta = 1; node
	 * = new Node<K,V>(h, key, val, null); } } finally { setTabAt(tab, i, node);
	 * } } } if (binCount != 0) break; } else if ((fh = f.hash) == MOVED) tab =
	 * helpTransfer(tab, f); else { synchronized (f) { if (tabAt(tab, i) == f) {
	 * if (fh >= 0) { binCount = 1; for (Node<K,V> e = f, pred = null;;
	 * ++binCount) { K ek; if (e.hash == h && ((ek = e.key) == key || (ek !=
	 * null && key.equals(ek)))) { val = remappingFunction.apply(key, e.val); if
	 * (val != null) e.val = val; else { delta = -1; Node<K,V> en = e.next; if
	 * (pred != null) pred.next = en; else setTabAt(tab, i, en); } break; } pred
	 * = e; if ((e = e.next) == null) { val = remappingFunction.apply(key,
	 * null); if (val != null) { delta = 1; pred.next = new Node<K,V>(h, key,
	 * val, null); } break; } } } else if (f instanceof TreeBin) { binCount = 1;
	 * TreeBin<K,V> t = (TreeBin<K,V>)f; TreeNode<K,V> r, p; if ((r = t.root) !=
	 * null) p = r.findTreeNode(h, key, null); else p = null; V pv = (p == null)
	 * ? null : p.val; val = remappingFunction.apply(key, pv); if (val != null)
	 * { if (p != null) p.val = val; else { delta = 1; t.putTreeVal(h, key,
	 * val); } } else if (p != null) { delta = -1; if (t.removeTreeNode(p))
	 * setTabAt(tab, i, untreeify(t.first)); } } } } if (binCount != 0) { if
	 * (binCount >= TREEIFY_THRESHOLD) treeifyBin(tab, i); break; } } } if
	 * (delta != 0) addCount((long)delta, binCount); return val; }
	 */
	/**
	 * If the specified key is not already associated with a (non-null) value,
	 * associates it with the given value. Otherwise, replaces the value with
	 * the results of the given remapping function, or removes if {@code null}.
	 * The entire method invocation is performed atomically. Some attempted
	 * update operations on this map by other threads may be blocked while
	 * computation is in progress, so the computation should be short and
	 * simple, and must not attempt to update any other mappings of this Map.
	 *
	 * @param key
	 *            key with which the specified value is to be associated
	 * @param value
	 *            the value to use if absent
	 * @param remappingFunction
	 *            the function to recompute a value if present
	 * @return the new value associated with the specified key, or null if none
	 * @throws NullPointerException
	 *             if the specified key or the remappingFunction is null
	 * @throws RuntimeException
	 *             or Error if the remappingFunction does so, in which case the
	 *             mapping is unchanged
	 */
	/*
	 * public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends
	 * V> remappingFunction) { if (key == null || value == null ||
	 * remappingFunction == null) throw new NullPointerException(); int h =
	 * spread(key.hashCode()); V val = null; int delta = 0; int binCount = 0;
	 * for (Node<K,V>[] tab = table;;) { Node<K,V> f; int n, i, fh; if (tab ==
	 * null || (n = tab.length) == 0) tab = initTable(); else if ((f =
	 * tabAt(tab, i = (n - 1) & h)) == null) { if (casTabAt(tab, i, null, new
	 * Node<K,V>(h, key, value, null))) { delta = 1; val = value; break; } }
	 * else if ((fh = f.hash) == MOVED) tab = helpTransfer(tab, f); else {
	 * synchronized (f) { if (tabAt(tab, i) == f) { if (fh >= 0) { binCount = 1;
	 * for (Node<K,V> e = f, pred = null;; ++binCount) { K ek; if (e.hash == h
	 * && ((ek = e.key) == key || (ek != null && key.equals(ek)))) { val =
	 * remappingFunction.apply(e.val, value); if (val != null) e.val = val; else
	 * { delta = -1; Node<K,V> en = e.next; if (pred != null) pred.next = en;
	 * else setTabAt(tab, i, en); } break; } pred = e; if ((e = e.next) == null)
	 * { delta = 1; val = value; pred.next = new Node<K,V>(h, key, val, null);
	 * break; } } } else if (f instanceof TreeBin) { binCount = 2; TreeBin<K,V>
	 * t = (TreeBin<K,V>)f; TreeNode<K,V> r = t.root; TreeNode<K,V> p = (r ==
	 * null) ? null : r.findTreeNode(h, key, null); val = (p == null) ? value :
	 * remappingFunction.apply(p.val, value); if (val != null) { if (p != null)
	 * p.val = val; else { delta = 1; t.putTreeVal(h, key, val); } } else if (p
	 * != null) { delta = -1; if (t.removeTreeNode(p)) setTabAt(tab, i,
	 * untreeify(t.first)); } } } } if (binCount != 0) { if (binCount >=
	 * TREEIFY_THRESHOLD) treeifyBin(tab, i); break; } } } if (delta != 0)
	 * addCount((long)delta, binCount); return val; }
	 */
	// Hashtable legacy methods

	/**
	 * Returns a {@link Set} view of the keys in this map, using the given
	 * common mapped value for any additions (i.e., {@link Collection#add} and
	 * {@link Collection#addAll(Collection)}). This is of course only
	 * appropriate if it is acceptable to use the same value for all additions
	 * from this view.
	 *
	 * @param mappedValue
	 *            the mapped value to use for any additions
	 * @return the set view
	 * @throws NullPointerException
	 *             if the mappedValue is null
	 */
	public KeySetView keySet(ArrayList<Object> mappedValue)
	{
		if (mappedValue == null)
		{
			throw new NullPointerException();
		}
		return new KeySetView(this, mappedValue);
	}

	/**
	 * Returns an enumeration of the keys in this table.
	 *
	 * @return an enumeration of the keys in this table
	 * @see #keySet()
	 */
	/*
	 * public Enumeration keys() { Node[] t; int f = (t = table) == null ? 0 :
	 * t.length; return new KeyIterator(t, f, 0, f, this); }
	 */

	/**
	 * Returns the number of mappings. This method should be used instead of
	 * {@link #size} because a ConcurrentHashMap may contain more mappings than
	 * can be represented as an int. The value returned is an estimate; the
	 * actual count may differ if there are concurrent insertions or removals.
	 *
	 * @return the number of mappings
	 * @since 1.8
	 */
	public long mappingCount()
	{
		long n = sumCount();
		return (n < 0L) ? 0L : n; // ignore transient negative values
	}

	// ConcurrentHashMap-only methods

	/**
	 * Maps the specified key to the specified value in this table. Neither the
	 * key nor the value can be null.
	 *
	 * <p>
	 * The value can be retrieved by calling the {@code get} method with a key
	 * that is equal to the original key.
	 *
	 * @param key
	 *            key with which the specified value is to be associated
	 * @param value
	 *            value to be associated with the specified key
	 * @return the previous value associated with {@code key}, or {@code null}
	 *         if there was no mapping for {@code key}
	 * @throws NullPointerException
	 *             if the specified key or value is null
	 */
	public ArrayList<Object> put(long key, ArrayList<Object> value)
	{
		// try
		// {
		return putVal(key, value, false);
		// }
		// catch(Throwable e)
		// {
		// HRDBMSWorker.logger.debug("", e);
		// return null;
		// }
	}

	/**
	 * Creates a new {@link Set} backed by a ConcurrentHashMap from the given
	 * type to {@code Boolean.TRUE}.
	 *
	 * @param <K>
	 *            the element type of the returned set
	 * @return the new set
	 * @since 1.8
	 */
	/*
	 * public static <K> KeySetView<K,Boolean> newKeySet() { return new
	 * KeySetView<K,Boolean> (new ConcurrentHashMap<K,Boolean>(), Boolean.TRUE);
	 * }
	 */

	/**
	 * Creates a new {@link Set} backed by a ConcurrentHashMap from the given
	 * type to {@code Boolean.TRUE}.
	 *
	 * @param initialCapacity
	 *            The implementation performs internal sizing to accommodate
	 *            this many elements.
	 * @param <K>
	 *            the element type of the returned set
	 * @return the new set
	 * @throws IllegalArgumentException
	 *             if the initial capacity of elements is negative
	 * @since 1.8
	 */
	/*
	 * public static <K> KeySetView<K,Boolean> newKeySet(int initialCapacity) {
	 * return new KeySetView<K,Boolean> (new
	 * ConcurrentHashMap<K,Boolean>(initialCapacity), Boolean.TRUE); }
	 */

	/**
	 * {@inheritDoc}
	 *
	 * @return the previous value associated with the specified key, or
	 *         {@code null} if there was no mapping for the key
	 * @throws NullPointerException
	 *             if the specified key or value is null
	 */
	public ArrayList<Object> putIfAbsent(long key, ArrayList<Object> value)
	{
		// try
		// {
		return putVal(key, value, true);
		// }
		// catch(Throwable e)
		// {
		// HRDBMSWorker.logger.debug("", e);
		// return null;
		// }
	}

	/* ---------------- Special Nodes -------------- */

	/**
	 * Removes the key (and its corresponding value) from this map. This method
	 * does nothing if the key is not in the map.
	 *
	 * @param key
	 *            the key that needs to be removed
	 * @return the previous value associated with {@code key}, or {@code null}
	 *         if there was no mapping for {@code key}
	 * @throws NullPointerException
	 *             if the specified key is null
	 */
	public ArrayList<Object> remove(long key)
	{
		// try
		// {
		return replaceNode(key, null, null);
		// }
		// catch(Throwable e)
		// {
		// HRDBMSWorker.logger.debug("", e);
		// return null;
		// }
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws NullPointerException
	 *             if the specified key is null
	 */
	public boolean remove(long key, ArrayList<Object> value)
	{
		if (key == -1)
		{
			throw new NullPointerException();
		}
		return value != null && replaceNode(key, null, value) != null;
	}

	/* ---------------- Table Initialization and Resizing -------------- */

	/**
	 * {@inheritDoc}
	 *
	 * @return the previous value associated with the specified key, or
	 *         {@code null} if there was no mapping for the key
	 * @throws NullPointerException
	 *             if the specified key or value is null
	 */
	public ArrayList<Object> replace(long key, ArrayList<Object> value)
	{
		// try
		// {
		if (key == -1 || value == null)
		{
			throw new NullPointerException();
		}
		return replaceNode(key, value, null);
		// }
		// catch(Throwable e)
		// {
		// HRDBMSWorker.logger.debug("", e);
		// return null;
		// }
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws NullPointerException
	 *             if any of the arguments are null
	 */
	public boolean replace(long key, ArrayList<Object> oldValue, ArrayList<Object> newValue)
	{
		if (key == -1 || oldValue == null || newValue == null)
		{
			throw new NullPointerException();
		}
		return replaceNode(key, newValue, oldValue) != null;
	}

	/**
	 * {@inheritDoc}
	 */
	public int size()
	{
		// try
		// {
		long n = sumCount();
		return ((n < 0L) ? 0 : (n > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int)n);
		// }
		// catch(Throwable e)
		// {
		// HRDBMSWorker.logger.debug("", e);
		// return -1;
		// }
	}

	/**
	 * Returns a string representation of this map. The string representation
	 * consists of a list of key-value mappings (in no particular order)
	 * enclosed in braces ("{@code "). Adjacent mappings are separated by the
	 * characters {@code ", "} (comma and space). Each key-value mapping is
	 * rendered as the key followed by an equals sign ("{@code =}") followed by
	 * the associated value.
	 *
	 * @return a string representation of this map
	 */
	@Override
	public String toString()
	{
		Node[] t;
		int f = (t = table) == null ? 0 : t.length;
		Traverser it = new Traverser(t, f, 0, f);
		StringBuilder sb = new StringBuilder();
		sb.append('{');
		Node p;
		if ((p = it.advance()) != null)
		{
			for (;;)
			{
				long k = p.key;
				ArrayList<Object> v = p.val;
				sb.append(k);
				sb.append('=');
				sb.append(v);
				if ((p = it.advance()) == null)
				{
					break;
				}
				sb.append(',').append(' ');
			}
		}
		return sb.append('}').toString();
	}

	/**
	 * Returns a {@link Collection} view of the values contained in this map.
	 * The collection is backed by the map, so changes to the map are reflected
	 * in the collection, and vice-versa. The collection supports element
	 * removal, which removes the corresponding mapping from this map, via the
	 * {@code Iterator.remove}, {@code Collection.remove}, {@code removeAll},
	 * {@code retainAll}, and {@code clear} operations. It does not support the
	 * {@code add} or {@code addAll} operations.
	 *
	 * <p>
	 * The view's iterators and spliterators are <a
	 * href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
	 *
	 * <p>
	 * The view's {@code spliterator} reports {@link Spliterator#CONCURRENT} and
	 * {@link Spliterator#NONNULL}.
	 *
	 * @return the collection view
	 */
	public ValuesView values()
	{
		// try
		// {
		ValuesView vs;
		return (vs = values) != null ? vs : (values = new ValuesView(this));
		// }
		// catch(Throwable e)
		// {
		// HRDBMSWorker.logger.debug("", e);
		// return null;
		// }
	}

	/**
	 * Adds to count, and if table is too small and not already resizing,
	 * initiates transfer. If already resizing, helps perform transfer if work
	 * is available. Rechecks occupancy after a transfer to see if another
	 * resize is already needed because resizings are lagging additions.
	 *
	 * @param x
	 *            the count to add
	 * @param check
	 *            if <0, don't check resize, if <= 1 only check if uncontended
	 */
	private final void addCount(long x, int check)
	{
		CounterCell[] as;
		long b, s;
		if ((as = counterCells) != null || !U.compareAndSwapLong(this, BASECOUNT, b = baseCount, s = b + x))
		{
			CounterCell a;
			long v;
			int m;
			boolean uncontended = true;
			if (as == null || (m = as.length - 1) < 0 || (a = as[MyThreadLocalRandom.getProbe() & m]) == null || !(uncontended = U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x)))
			{
				fullAddCount(x, uncontended);
				return;
			}
			if (check <= 1)
			{
				return;
			}
			s = sumCount();
		}
		if (check >= 0)
		{
			Node[] tab, nt;
			int n, sc;
			while (s >= (sc = sizeCtl) && (tab = table) != null && (n = tab.length) < MAXIMUM_CAPACITY)
			{
				int rs = resizeStamp(n);
				if (sc < 0)
				{
					if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 || sc == rs + MAX_RESIZERS || (nt = nextTable) == null || transferIndex <= 0)
					{
						break;
					}
					if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1))
					{
						transfer(tab, nt);
					}
				}
				else if (U.compareAndSwapInt(this, SIZECTL, sc, (rs << RESIZE_STAMP_SHIFT) + 2))
				{
					transfer(tab, null);
				}
				s = sumCount();
			}
		}
	}

	/* ---------------- Counter support -------------- */

	// See LongAdder version for explanation
	private final void fullAddCount(long x, boolean wasUncontended)
	{
		int h;
		if ((h = MyThreadLocalRandom.getProbe()) == 0)
		{
			MyThreadLocalRandom.localInit(); // force initialization
			h = MyThreadLocalRandom.getProbe();
			wasUncontended = true;
		}
		boolean collide = false; // True if last slot nonempty
		for (;;)
		{
			CounterCell[] as;
			CounterCell a;
			int n;
			long v;
			if ((as = counterCells) != null && (n = as.length) > 0)
			{
				if ((a = as[(n - 1) & h]) == null)
				{
					if (cellsBusy == 0)
					{ // Try to attach new Cell
						CounterCell r = new CounterCell(x); // Optimistic create
						if (cellsBusy == 0 && U.compareAndSwapInt(this, CELLSBUSY, 0, 1))
						{
							boolean created = false;
							try
							{ // Recheck under lock
								CounterCell[] rs;
								int m, j;
								if ((rs = counterCells) != null && (m = rs.length) > 0 && rs[j = (m - 1) & h] == null)
								{
									rs[j] = r;
									created = true;
								}
							}
							finally
							{
								cellsBusy = 0;
							}
							if (created)
							{
								break;
							}
							continue; // Slot is now non-empty
						}
					}
					collide = false;
				}
				else if (!wasUncontended)
				{
					wasUncontended = true; // Continue after rehash
				}
				else if (U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))
				{
					break;
				}
				else if (counterCells != as || n >= NCPU)
				{
					collide = false; // At max size or stale
				}
				else if (!collide)
				{
					collide = true;
				}
				else if (cellsBusy == 0 && U.compareAndSwapInt(this, CELLSBUSY, 0, 1))
				{
					try
					{
						if (counterCells == as)
						{// Expand table unless stale
							CounterCell[] rs = new CounterCell[n << 1];
							for (int i = 0; i < n; ++i)
							{
								rs[i] = as[i];
							}
							counterCells = rs;
						}
					}
					finally
					{
						cellsBusy = 0;
					}
					collide = false;
					continue; // Retry with expanded table
				}
				h = MyThreadLocalRandom.advanceProbe(h);
			}
			else if (cellsBusy == 0 && counterCells == as && U.compareAndSwapInt(this, CELLSBUSY, 0, 1))
			{
				boolean init = false;
				try
				{ // Initialize table
					if (counterCells == as)
					{
						CounterCell[] rs = new CounterCell[2];
						rs[h & 1] = new CounterCell(x);
						counterCells = rs;
						init = true;
					}
				}
				finally
				{
					cellsBusy = 0;
				}
				if (init)
				{
					break;
				}
			}
			else if (U.compareAndSwapLong(this, BASECOUNT, v = baseCount, v + x))
			{
				break; // Fall back on using base
			}
		}
	}

	/**
	 * Initializes table, using the size recorded in sizeCtl.
	 */
	private final Node[] initTable()
	{
		Node[] tab;
		int sc;
		while ((tab = table) == null || tab.length == 0)
		{
			if ((sc = sizeCtl) < 0)
			{
				Thread.yield(); // lost initialization race; just spin
			}
			else if (U.compareAndSwapInt(this, SIZECTL, sc, -1))
			{
				try
				{
					if ((tab = table) == null || tab.length == 0)
					{
						int n = (sc > 0) ? sc : DEFAULT_CAPACITY;
						@SuppressWarnings("unchecked")
						Node[] nt = new Node[n];
						table = tab = nt;
						sc = n - (n >>> 2);
					}
				}
				finally
				{
					sizeCtl = sc;
				}
				break;
			}
		}
		return tab;
	}

	/**
	 * Moves and/or copies the nodes in each bin to new table. See above for
	 * explanation.
	 */
	private final void transfer(Node[] tab, Node[] nextTab)
	{
		int n = tab.length, stride;
		if ((stride = (NCPU > 1) ? (n >>> 3) / NCPU : n) < MIN_TRANSFER_STRIDE)
		{
			stride = MIN_TRANSFER_STRIDE; // subdivide range
		}
		if (nextTab == null)
		{ // initiating
			try
			{
				@SuppressWarnings("unchecked")
				Node[] nt = new Node[n << 1];
				nextTab = nt;
			}
			catch (Throwable ex)
			{ // try to cope with OOME
				sizeCtl = Integer.MAX_VALUE;
				return;
			}
			nextTable = nextTab;
			transferIndex = n;
		}
		int nextn = nextTab.length;
		ForwardingNode fwd = new ForwardingNode(nextTab);
		boolean advance = true;
		boolean finishing = false; // to ensure sweep before committing nextTab
		for (int i = 0, bound = 0;;)
		{
			Node f;
			int fh;
			while (advance)
			{
				int nextIndex, nextBound;
				if (--i >= bound || finishing)
				{
					advance = false;
				}
				else if ((nextIndex = transferIndex) <= 0)
				{
					i = -1;
					advance = false;
				}
				else if (U.compareAndSwapInt(this, TRANSFERINDEX, nextIndex, nextBound = (nextIndex > stride ? nextIndex - stride : 0)))
				{
					bound = nextBound;
					i = nextIndex - 1;
					advance = false;
				}
			}
			if (i < 0 || i >= n || i + n >= nextn)
			{
				int sc;
				if (finishing)
				{
					nextTable = null;
					table = nextTab;
					sizeCtl = (n << 1) - (n >>> 1);
					return;
				}
				if (U.compareAndSwapInt(this, SIZECTL, sc = sizeCtl, sc - 1))
				{
					if ((sc - 2) != resizeStamp(n) << RESIZE_STAMP_SHIFT)
					{
						return;
					}
					finishing = advance = true;
					i = n; // recheck before commit
				}
			}
			else if ((f = tabAt(tab, i)) == null)
			{
				advance = casTabAt(tab, i, null, fwd);
			}
			else if ((fh = f.hash) == MOVED)
			{
				advance = true; // already processed
			}
			else
			{
				synchronized (f)
				{
					if (tabAt(tab, i) == f)
					{
						Node ln, hn;
						if (fh >= 0)
						{
							int runBit = fh & n;
							Node lastRun = f;
							for (Node p = f.next; p != null; p = p.next)
							{
								int b = p.hash & n;
								if (b != runBit)
								{
									runBit = b;
									lastRun = p;
								}
							}
							if (runBit == 0)
							{
								ln = lastRun;
								hn = null;
							}
							else
							{
								hn = lastRun;
								ln = null;
							}
							for (Node p = f; p != lastRun; p = p.next)
							{
								int ph = p.hash;
								long pk = p.key;
								ArrayList<Object> pv = p.val;
								if ((ph & n) == 0)
								{
									ln = new Node(ph, pk, pv, ln);
								}
								else
								{
									hn = new Node(ph, pk, pv, hn);
								}
							}
							setTabAt(nextTab, i, ln);
							setTabAt(nextTab, i + n, hn);
							setTabAt(tab, i, fwd);
							advance = true;
						}
						else if (f instanceof TreeBin)
						{
							TreeBin t = (TreeBin)f;
							TreeNode lo = null, loTail = null;
							TreeNode hi = null, hiTail = null;
							int lc = 0, hc = 0;
							for (Node e = t.first; e != null; e = e.next)
							{
								int h = e.hash;
								TreeNode p = new TreeNode(h, e.key, e.val, null, null);
								if ((h & n) == 0)
								{
									if ((p.prev = loTail) == null)
									{
										lo = p;
									}
									else
									{
										loTail.next = p;
									}
									loTail = p;
									++lc;
								}
								else
								{
									if ((p.prev = hiTail) == null)
									{
										hi = p;
									}
									else
									{
										hiTail.next = p;
									}
									hiTail = p;
									++hc;
								}
							}
							ln = (lc <= UNTREEIFY_THRESHOLD) ? untreeify(lo) : (hc != 0) ? new TreeBin(lo) : t;
							hn = (hc <= UNTREEIFY_THRESHOLD) ? untreeify(hi) : (lc != 0) ? new TreeBin(hi) : t;
							setTabAt(nextTab, i, ln);
							setTabAt(nextTab, i + n, hn);
							setTabAt(tab, i, fwd);
							advance = true;
						}
					}
				}
			}
		}
	}

	/* ---------------- Conversion from/to TreeBins -------------- */

	/**
	 * Replaces all linked nodes in bin at given index unless table is too
	 * small, in which case resizes instead.
	 */
	private final void treeifyBin(Node[] tab, int index)
	{
		Node b;
		int n;
		if (tab != null)
		{
			if ((n = tab.length) < MIN_TREEIFY_CAPACITY)
			{
				tryPresize(n << 1);
			}
			else if ((b = tabAt(tab, index)) != null && b.hash >= 0)
			{
				synchronized (b)
				{
					if (tabAt(tab, index) == b)
					{
						TreeNode hd = null, tl = null;
						for (Node e = b; e != null; e = e.next)
						{
							TreeNode p = new TreeNode(e.hash, e.key, e.val, null, null);
							if ((p.prev = tl) == null)
							{
								hd = p;
							}
							else
							{
								tl.next = p;
							}
							tl = p;
						}
						setTabAt(tab, index, new TreeBin(hd));
					}
				}
			}
		}
	}

	/**
	 * Tries to presize table to accommodate the given number of elements.
	 *
	 * @param size
	 *            number of elements (doesn't need to be perfectly accurate)
	 */
	private final void tryPresize(int size)
	{
		int c = (size >= (MAXIMUM_CAPACITY >>> 1)) ? MAXIMUM_CAPACITY : tableSizeFor(size + (size >>> 1) + 1);
		int sc;
		while ((sc = sizeCtl) >= 0)
		{
			Node[] tab = table;
			int n;
			if (tab == null || (n = tab.length) == 0)
			{
				n = (sc > c) ? sc : c;
				if (U.compareAndSwapInt(this, SIZECTL, sc, -1))
				{
					try
					{
						if (table == tab)
						{
							@SuppressWarnings("unchecked")
							Node[] nt = new Node[n];
							table = nt;
							sc = n - (n >>> 2);
						}
					}
					finally
					{
						sizeCtl = sc;
					}
				}
			}
			else if (c <= sc || n >= MAXIMUM_CAPACITY)
			{
				break;
			}
			else if (tab == table)
			{
				int rs = resizeStamp(n);
				if (sc < 0)
				{
					Node[] nt;
					if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 || sc == rs + MAX_RESIZERS || (nt = nextTable) == null || transferIndex <= 0)
					{
						break;
					}
					if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1))
					{
						transfer(tab, nt);
					}
				}
				else if (U.compareAndSwapInt(this, SIZECTL, sc, (rs << RESIZE_STAMP_SHIFT) + 2))
				{
					transfer(tab, null);
				}
			}
		}
	}

	/* ---------------- TreeNodes -------------- */

	/**
	 * Saves the state of the {@code ConcurrentHashMap} instance to a stream
	 * (i.e., serializes it).
	 *
	 * @param s
	 *            the stream
	 * @throws java.io.IOException
	 *             if an I/O error occurs
	 * @serialData the key (Object) and value (Object) for each key-value
	 *             mapping, followed by a null pair. The key-value mappings are
	 *             emitted in no particular order.
	 */
	private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException
	{
		// For serialization compatibility
		// Emulate segment calculation from previous version of this class
		int sshift = 0;
		int ssize = 1;
		while (ssize < DEFAULT_CONCURRENCY_LEVEL)
		{
			++sshift;
			ssize <<= 1;
		}
		int segmentShift = 32 - sshift;
		int segmentMask = ssize - 1;
		@SuppressWarnings("unchecked")
		Segment[] segments = new Segment[DEFAULT_CONCURRENCY_LEVEL];
		for (int i = 0; i < segments.length; ++i)
		{
			segments[i] = new Segment(LOAD_FACTOR);
		}
		s.putFields().put("segments", segments);
		s.putFields().put("segmentShift", segmentShift);
		s.putFields().put("segmentMask", segmentMask);
		s.writeFields();

		Node[] t;
		if ((t = table) != null)
		{
			Traverser it = new Traverser(t, t.length, 0, t.length);
			for (Node p; (p = it.advance()) != null;)
			{
				s.writeObject(p.key);
				s.writeObject(p.val);
			}
		}
		s.writeObject(null);
		s.writeObject(null);
		segments = null; // throw away
	}

	/* ---------------- TreeBins -------------- */

	/**
	 * Computes initial batch value for bulk tasks. The returned value is
	 * approximately exp2 of the number of times (minus one) to split task by
	 * two before executing leaf action. This value is faster to compute and
	 * more convenient to use as a guide to splitting than is the depth, since
	 * it is used while dividing by two anyway.
	 */
	final int batchFor(long b)
	{
		long n;
		if (b == Long.MAX_VALUE || (n = sumCount()) <= 1L || n < b)
		{
			return 0;
		}
		int sp = ForkJoinPool.getCommonPoolParallelism() << 2; // slack of 4
		return (b <= 0L || (n /= b) >= sp) ? sp : (int)n;
	}

	/* ----------------Table Traversal -------------- */

	/**
	 * Helps transfer if a resize is in progress.
	 */
	final Node[] helpTransfer(Node[] tab, Node f)
	{
		Node[] nextTab;
		int sc;
		if (tab != null && (f instanceof ForwardingNode) && (nextTab = ((ForwardingNode)f).nextTable) != null)
		{
			int rs = resizeStamp(tab.length);
			while (nextTab == nextTable && table == tab && (sc = sizeCtl) < 0)
			{
				if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 || sc == rs + MAX_RESIZERS || transferIndex <= 0)
				{
					break;
				}
				if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1))
				{
					transfer(tab, nextTab);
					break;
				}
			}
			return nextTab;
		}
		return table;
	}

	/** Implementation for put and putIfAbsent */
	final ArrayList<Object> putVal(long key, ArrayList<Object> value, boolean onlyIfAbsent)
	{
		if (key == -1 || value == null)
		{
			throw new NullPointerException();
		}
		int hash = spread((int)(key ^ (key >>> 32)));
		int binCount = 0;
		for (Node[] tab = table;;)
		{
			Node f;
			int n, i, fh;
			if (tab == null || (n = tab.length) == 0)
			{
				tab = initTable();
			}
			else if ((f = tabAt(tab, i = (n - 1) & hash)) == null)
			{
				if (casTabAt(tab, i, null, new Node(hash, key, value, null)))
				{
					break; // no lock when adding to empty bin
				}
			}
			else if ((fh = f.hash) == MOVED)
			{
				tab = helpTransfer(tab, f);
			}
			else
			{
				ArrayList<Object> oldVal = null;
				synchronized (f)
				{
					if (tabAt(tab, i) == f)
					{
						if (fh >= 0)
						{
							binCount = 1;
							for (Node e = f;; ++binCount)
							{
								if (e.hash == hash && ((e.key) == key))
								{
									oldVal = e.val;
									if (!onlyIfAbsent)
									{
										e.val = value;
									}
									break;
								}
								Node pred = e;
								if ((e = e.next) == null)
								{
									pred.next = new Node(hash, key, value, null);
									break;
								}
							}
						}
						else if (f instanceof TreeBin)
						{
							Node p;
							binCount = 2;
							if ((p = ((TreeBin)f).putTreeVal(hash, key, value)) != null)
							{
								oldVal = p.val;
								if (!onlyIfAbsent)
								{
									p.val = value;
								}
							}
						}
					}
				}
				if (binCount != 0)
				{
					if (binCount >= TREEIFY_THRESHOLD)
					{
						treeifyBin(tab, i);
					}
					if (oldVal != null)
					{
						return oldVal;
					}
					break;
				}
			}
		}
		addCount(1L, binCount);
		return null;
	}

	/**
	 * Implementation for the four public remove/replace methods: Replaces node
	 * value with v, conditional upon match of cv if non-null. If resulting
	 * value is null, delete.
	 */
	final ArrayList<Object> replaceNode(long key, ArrayList<Object> value, Object cv)
	{
		int hash = spread((int)(key ^ (key >>> 32)));
		for (Node[] tab = table;;)
		{
			Node f;
			int n, i, fh;
			if (tab == null || (n = tab.length) == 0 || (f = tabAt(tab, i = (n - 1) & hash)) == null)
			{
				break;
			}
			else if ((fh = f.hash) == MOVED)
			{
				tab = helpTransfer(tab, f);
			}
			else
			{
				ArrayList<Object> oldVal = null;
				boolean validated = false;
				synchronized (f)
				{
					if (tabAt(tab, i) == f)
					{
						if (fh >= 0)
						{
							validated = true;
							for (Node e = f, pred = null;;)
							{
								if (e.hash == hash && ((e.key) == key))
								{
									ArrayList<Object> ev = e.val;
									if (cv == null || cv == ev || (ev != null && cv.equals(ev)))
									{
										oldVal = ev;
										if (value != null)
										{
											e.val = value;
										}
										else if (pred != null)
										{
											pred.next = e.next;
										}
										else
										{
											setTabAt(tab, i, e.next);
										}
									}
									break;
								}
								pred = e;
								if ((e = e.next) == null)
								{
									break;
								}
							}
						}
						else if (f instanceof TreeBin)
						{
							validated = true;
							TreeBin t = (TreeBin)f;
							TreeNode r, p;
							if ((r = t.root) != null && (p = r.findTreeNode(hash, key, null)) != null)
							{
								ArrayList<Object> pv = p.val;
								if (cv == null || cv == pv || (pv != null && cv.equals(pv)))
								{
									oldVal = pv;
									if (value != null)
									{
										p.val = value;
									}
									else if (t.removeTreeNode(p))
									{
										setTabAt(tab, i, untreeify(t.first));
									}
								}
							}
						}
					}
				}
				if (validated)
				{
					if (oldVal != null)
					{
						if (value == null)
						{
							addCount(-1L, -1);
						}
						return oldVal;
					}
					break;
				}
			}
		}
		return null;
	}

	final long sumCount()
	{
		CounterCell[] as = counterCells;
		CounterCell a;
		long sum = baseCount;
		if (as != null)
		{
			for (CounterCell element : as)
			{
				if ((a = element) != null)
				{
					sum += a.value;
				}
			}
		}
		return sum;
	}

	static public final class EntryIterator extends BaseIterator
	{
		EntryIterator(Node[] tab, int index, int size, int limit, InternalConcurrentHashMap map)
		{
			super(tab, index, size, limit, map);
		}

		public final MapEntry next()
		{
			// try
			// {
			Node p;
			if ((p = next) == null)
			{
				throw new NoSuchElementException();
			}
			long k = p.key;
			ArrayList<Object> v = p.val;
			lastReturned = p;
			advance();
			return new MapEntry(k, v, map);
			// }
			// catch(Throwable e)
			// {
			// HRDBMSWorker.logger.debug("", e);
			// return null;
			// }
		}
	}

	/**
	 * A view of a ConcurrentHashMap as a {@link Set} of (key, value) entries.
	 * This class cannot be directly instantiated. See {@link #entrySet()}.
	 */
	static public final class EntrySetView extends CollectionView
	{
		EntrySetView(InternalConcurrentHashMap map)
		{
			super(map);
		}

		public boolean contains(Object o)
		{
			long k;
			ArrayList<Object> v, r;
			Node e;
			return ((o instanceof Node) && (k = (e = (Node)o).getKey()) != -1 && (r = map.get(k)) != null && (v = e.getValue()) != null && (v == r || v.equals(r)));
		}

		@Override
		public final int hashCode()
		{
			int h = 0;
			Node[] t;
			if ((t = map.table) != null)
			{
				Traverser it = new Traverser(t, t.length, 0, t.length);
				for (Node p; (p = it.advance()) != null;)
				{
					h += p.hashCode();
				}
			}
			return h;
		}

		/**
		 * @return an iterator over the entries of the backing map
		 */
		public EntryIterator iterator()
		{
			// try
			// {
			InternalConcurrentHashMap m = map;
			Node[] t;
			int f = (t = m.table) == null ? 0 : t.length;
			return new EntryIterator(t, f, 0, f, m);
			// }
			// catch(Throwable e)
			// {
			// HRDBMSWorker.logger.debug("", e);
			// return null;
			// }
		}

		/*
		 * public boolean add(Entry e) { return map.putVal(e.getKey(),
		 * e.getValue(), false) == null; }
		 *
		 * public boolean addAll(Collection<? extends Entry> c) { boolean added
		 * = false; for (Entry e : c) { if (add(e)) added = true; } return
		 * added; }
		 */

		public boolean remove(Object o)
		{
			long k;
			ArrayList<Object> v;
			Node e;
			return ((o instanceof Node) && (k = (e = (Node)o).getKey()) != -1 && (v = e.getValue()) != null && map.remove(k, v));
		}

		/*
		 * public final boolean equals(Object o) { Set<?> c; return ((o
		 * instanceof Set) && ((c = (Set<?>)o) == this || (containsAll(c) &&
		 * c.containsAll(this)))); }
		 *
		 * public Spliterator<Map.Entry> spliterator() { Node[] t;
		 * InternalConcurrentHashMap m = map; long n = m.sumCount(); int f = (t
		 * = m.table) == null ? 0 : t.length; return new EntrySpliterator(t, f,
		 * 0, f, n < 0L ? 0L : n, m); }
		 *
		 * public void forEach(Consumer<? super Map.Entry> action) { if (action
		 * == null) throw new NullPointerException(); Node[] t; if ((t =
		 * map.table) != null) { Traverser it = new Traverser(t, t.length, 0,
		 * t.length); for (Node p; (p = it.advance()) != null; )
		 * action.accept(new MapEntry(p.key, p.val, map)); } }
		 */

	}

	static public final class KeyIterator extends BaseIterator
	{
		KeyIterator(Node[] tab, int index, int size, int limit, InternalConcurrentHashMap map)
		{
			super(tab, index, size, limit, map);
		}

		public final long next()
		{
			Node p;
			if ((p = next) == null)
			{
				throw new NoSuchElementException();
			}
			long k = p.key;
			lastReturned = p;
			advance();
			return k;
		}

		public final long nextElement()
		{
			return next();
		}
	}

	/*
	 * static final class KeySpliterator extends Traverser implements
	 * Spliterator { long est; // size estimate KeySpliterator(Node[] tab, int
	 * size, int index, int limit, long est) { super(tab, size, index, limit);
	 * this.est = est; }
	 *
	 * public Spliterator trySplit() { int i, f, h; return (h = ((i = baseIndex)
	 * + (f = baseLimit)) >>> 1) <= i ? null : new KeySpliterator(tab, baseSize,
	 * baseLimit = h, f, est >>>= 1); }
	 *
	 * /* public void forEachRemaining(Consumer<? super K> action) { if (action
	 * == null) throw new NullPointerException(); for (Node<K,V> p; (p =
	 * advance()) != null;) action.accept(p.key); }
	 *
	 * public boolean tryAdvance(Consumer<? super K> action) { if (action ==
	 * null) throw new NullPointerException(); Node<K,V> p; if ((p = advance())
	 * == null) return false; action.accept(p.key); return true; }
	 */
	/*
	 * public long estimateSize() { return est; }
	 *
	 * public int characteristics() { return Spliterator.DISTINCT |
	 * Spliterator.CONCURRENT | Spliterator.NONNULL; } }
	 */

	/*
	 * static final class ValueSpliterator extends Traverser implements
	 * Spliterator { long est; // size estimate ValueSpliterator(Node[] tab, int
	 * size, int index, int limit, long est) { super(tab, size, index, limit);
	 * this.est = est; }
	 *
	 * public Spliterator<ArrayList<Object>> trySplit() { int i, f, h; return (h
	 * = ((i = baseIndex) + (f = baseLimit)) >>> 1) <= i ? null : new
	 * ValueSpliterator(tab, baseSize, baseLimit = h, f, est >>>= 1); }
	 *
	 * /* public void forEachRemaining(Consumer<? super V> action) { if (action
	 * == null) throw new NullPointerException(); for (Node p; (p = advance())
	 * != null;) action.accept(p.val); }
	 *
	 * public boolean tryAdvance(Consumer<? super V> action) { if (action ==
	 * null) throw new NullPointerException(); Node p; if ((p = advance()) ==
	 * null) return false; action.accept(p.val); return true; }
	 */

	/*
	 * public long estimateSize() { return est; }
	 *
	 * public int characteristics() { return Spliterator.CONCURRENT |
	 * Spliterator.NONNULL; } }
	 */
	/*
	 * static final class EntrySpliterator extends Traverser implements
	 * Spliterator<Map.Entry> { final InternalConcurrentHashMap map; // To
	 * export MapEntry long est; // size estimate EntrySpliterator(Node[] tab,
	 * int size, int index, int limit, long est, InternalConcurrentHashMap map)
	 * { super(tab, size, index, limit); this.map = map; this.est = est; }
	 *
	 * public Spliterator<Map.Entry> trySplit() { int i, f, h; return (h = ((i =
	 * baseIndex) + (f = baseLimit)) >>> 1) <= i ? null : new
	 * EntrySpliterator(tab, baseSize, baseLimit = h, f, est >>>= 1, map); }
	 *
	 * public void forEachRemaining(Consumer<? super Map.Entry> action) { if
	 * (action == null) throw new NullPointerException(); for (Node p; (p =
	 * advance()) != null; ) action.accept(new MapEntry(p.key, p.val, map)); }
	 *
	 * public boolean tryAdvance(Consumer<? super Map.Entry> action) { if
	 * (action == null) throw new NullPointerException(); Node p; if ((p =
	 * advance()) == null) return false; action.accept(new MapEntry(p.key,
	 * p.val, map)); return true; }
	 *
	 * public long estimateSize() { return est; }
	 *
	 * public int characteristics() { return Spliterator.DISTINCT |
	 * Spliterator.CONCURRENT | Spliterator.NONNULL; } }
	 */

	// Parallel bulk operations

	/**
	 * A view of a ConcurrentHashMap as a {@link Set} of keys, in which
	 * additions may optionally be enabled by mapping to a common value. This
	 * class cannot be directly instantiated. See {@link #keySet() keySet()},
	 * {@link #keySet(Object) keySet(V)}, {@link #newKeySet() newKeySet()},
	 * {@link #newKeySet(int) newKeySet(int)}.
	 *
	 * @since 1.8
	 */
	public static class KeySetView extends CollectionView
	{
		private final ArrayList<Object> value;

		KeySetView(InternalConcurrentHashMap map, ArrayList<Object> value)
		{ // non-public
			super(map);
			this.value = value;
		}

		/**
		 * Adds the specified key to this set view by mapping the key to the
		 * default mapped value in the backing map, if defined.
		 *
		 * @param e
		 *            key to be added
		 * @return {@code true} if this set changed as a result of the call
		 * @throws NullPointerException
		 *             if the specified key is null
		 * @throws UnsupportedOperationException
		 *             if no default mapped value for additions was provided
		 */
		public boolean add(long e)
		{
			ArrayList<Object> v;
			if ((v = value) == null)
			{
				throw new UnsupportedOperationException();
			}
			return map.putVal(e, v, true) == null;
		}

		/**
		 * {@inheritDoc}
		 *
		 * @throws NullPointerException
		 *             if the specified key is null
		 */
		public boolean contains(long o)
		{
			return map.containsKey(o);
		}

		/**
		 * Returns the default mapped value for additions, or {@code null} if
		 * additions are not supported.
		 *
		 * @return the default mapped value for additions, or {@code null} if
		 *         not supported
		 */
		public ArrayList<Object> getMappedValue()
		{
			return value;
		}

		/**
		 * @return an iterator over the keys of the backing map
		 */
		public KeyIterator iterator()
		{
			Node[] t;
			InternalConcurrentHashMap m = map;
			int f = (t = m.table) == null ? 0 : t.length;
			return new KeyIterator(t, f, 0, f, m);
		}

		/**
		 * Removes the key from this map view, by removing the key (and its
		 * corresponding value) from the backing map. This method does nothing
		 * if the key is not in the map.
		 *
		 * @param o
		 *            the key to be removed from the backing map
		 * @return {@code true} if the backing map contained the specified key
		 * @throws NullPointerException
		 *             if the specified key is null
		 */
		public boolean remove(long o)
		{
			return map.remove(o) != null;
		}

		/**
		 * Adds all of the elements in the specified collection to this set, as
		 * if by calling {@link #add} on each one.
		 *
		 * @param c
		 *            the elements to be inserted into this set
		 * @return {@code true} if this set changed as a result of the call
		 * @throws NullPointerException
		 *             if the collection or any of its elements are {@code null}
		 * @throws UnsupportedOperationException
		 *             if no default mapped value for additions was provided
		 */
		/*
		 * public boolean addAll(Collection<? extends K> c) { boolean added =
		 * false; V v; if ((v = value) == null) throw new
		 * UnsupportedOperationException(); for (K e : c) { if (map.putVal(e, v,
		 * true) == null) added = true; } return added; }
		 */

		/*
		 * public int hashCode() { int h = 0; for (long e : this) h +=
		 * e.hashCode(); return h; }
		 */

		/*
		 * public boolean equals(Object o) { Set<?> c; return ((o instanceof
		 * Set) && ((c = (Set<?>)o) == this || (containsAll(c) &&
		 * c.containsAll(this)))); }
		 */

		/*
		 * public Spliterator spliterator() { Node[] t;
		 * InternalConcurrentHashMap m = map; long n = m.sumCount(); int f = (t
		 * = m.table) == null ? 0 : t.length; return new KeySpliterator(t, f, 0,
		 * f, n < 0L ? 0L : n); }
		 */

		/*
		 * public void forEach(Consumer<? super K> action) { if (action == null)
		 * throw new NullPointerException(); Node<K,V>[] t; if ((t = map.table)
		 * != null) { Traverser<K,V> it = new Traverser<K,V>(t, t.length, 0,
		 * t.length); for (Node<K,V> p; (p = it.advance()) != null; )
		 * action.accept(p.key); } }
		 */
	}

	/**
	 * Performs the given action for each (key, value).
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param action
	 *            the action
	 * @since 1.8
	 */
	/*
	 * public void forEach(long parallelismThreshold, BiConsumer<? super K,?
	 * super V> action) { if (action == null) throw new NullPointerException();
	 * new ForEachMappingTask<K,V> (null, batchFor(parallelismThreshold), 0, 0,
	 * table, action).invoke(); }
	 */
	/**
	 * Performs the given action for each non-null transformation of each (key,
	 * value).
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param transformer
	 *            a function returning the transformation for an element, or
	 *            null if there is no transformation (in which case the action
	 *            is not applied)
	 * @param action
	 *            the action
	 * @param <U>
	 *            the return type of the transformer
	 * @since 1.8
	 */
	/*
	 * public <U> void forEach(long parallelismThreshold, BiFunction<? super K,
	 * ? super V, ? extends U> transformer, Consumer<? super U> action) { if
	 * (transformer == null || action == null) throw new NullPointerException();
	 * new ForEachTransformedMappingTask<K,V,U> (null,
	 * batchFor(parallelismThreshold), 0, 0, table, transformer,
	 * action).invoke(); }
	 */
	/**
	 * Returns a non-null result from applying the given search function on each
	 * (key, value), or null if none. Upon success, further element processing
	 * is suppressed and the results of any other parallel invocations of the
	 * search function are ignored.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param searchFunction
	 *            a function returning a non-null result on success, else null
	 * @param <U>
	 *            the return type of the search function
	 * @return a non-null result from applying the given search function on each
	 *         (key, value), or null if none
	 * @since 1.8
	 */
	/*
	 * public <U> U search(long parallelismThreshold, BiFunction<? super K, ?
	 * super V, ? extends U> searchFunction) { if (searchFunction == null) throw
	 * new NullPointerException(); return new SearchMappingsTask<K,V,U> (null,
	 * batchFor(parallelismThreshold), 0, 0, table, searchFunction, new
	 * AtomicReference<U>()).invoke(); }
	 */
	/**
	 * Returns the result of accumulating the given transformation of all (key,
	 * value) pairs using the given reducer to combine values, or null if none.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param transformer
	 *            a function returning the transformation for an element, or
	 *            null if there is no transformation (in which case it is not
	 *            combined)
	 * @param reducer
	 *            a commutative associative combining function
	 * @param <U>
	 *            the return type of the transformer
	 * @return the result of accumulating the given transformation of all (key,
	 *         value) pairs
	 * @since 1.8
	 */
	/*
	 * public <U> U reduce(long parallelismThreshold, BiFunction<? super K, ?
	 * super V, ? extends U> transformer, BiFunction<? super U, ? super U, ?
	 * extends U> reducer) { if (transformer == null || reducer == null) throw
	 * new NullPointerException(); return new MapReduceMappingsTask<K,V,U>
	 * (null, batchFor(parallelismThreshold), 0, 0, table, null, transformer,
	 * reducer).invoke(); }
	 */
	/**
	 * Returns the result of accumulating the given transformation of all (key,
	 * value) pairs using the given reducer to combine values, and the given
	 * basis as an identity value.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param transformer
	 *            a function returning the transformation for an element
	 * @param basis
	 *            the identity (initial default value) for the reduction
	 * @param reducer
	 *            a commutative associative combining function
	 * @return the result of accumulating the given transformation of all (key,
	 *         value) pairs
	 * @since 1.8
	 */
	/*
	 * public double reduceToDouble(long parallelismThreshold,
	 * ToDoubleBiFunction<? super K, ? super V> transformer, double basis,
	 * DoubleBinaryOperator reducer) { if (transformer == null || reducer ==
	 * null) throw new NullPointerException(); return new
	 * MapReduceMappingsToDoubleTask<K,V> (null, batchFor(parallelismThreshold),
	 * 0, 0, table, null, transformer, basis, reducer).invoke(); }
	 */
	/**
	 * Returns the result of accumulating the given transformation of all (key,
	 * value) pairs using the given reducer to combine values, and the given
	 * basis as an identity value.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param transformer
	 *            a function returning the transformation for an element
	 * @param basis
	 *            the identity (initial default value) for the reduction
	 * @param reducer
	 *            a commutative associative combining function
	 * @return the result of accumulating the given transformation of all (key,
	 *         value) pairs
	 * @since 1.8
	 */
	/*
	 * public long reduceToLong(long parallelismThreshold, ToLongBiFunction<?
	 * super K, ? super V> transformer, long basis, LongBinaryOperator reducer)
	 * { if (transformer == null || reducer == null) throw new
	 * NullPointerException(); return new MapReduceMappingsToLongTask<K,V>
	 * (null, batchFor(parallelismThreshold), 0, 0, table, null, transformer,
	 * basis, reducer).invoke(); }
	 */
	/**
	 * Returns the result of accumulating the given transformation of all (key,
	 * value) pairs using the given reducer to combine values, and the given
	 * basis as an identity value.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param transformer
	 *            a function returning the transformation for an element
	 * @param basis
	 *            the identity (initial default value) for the reduction
	 * @param reducer
	 *            a commutative associative combining function
	 * @return the result of accumulating the given transformation of all (key,
	 *         value) pairs
	 * @since 1.8
	 */
	/*
	 * public int reduceToInt(long parallelismThreshold, ToIntBiFunction<? super
	 * K, ? super V> transformer, int basis, IntBinaryOperator reducer) { if
	 * (transformer == null || reducer == null) throw new
	 * NullPointerException(); return new MapReduceMappingsToIntTask<K,V> (null,
	 * batchFor(parallelismThreshold), 0, 0, table, null, transformer, basis,
	 * reducer).invoke(); }
	 */
	/**
	 * Performs the given action for each key.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param action
	 *            the action
	 * @since 1.8
	 */
	/*
	 * public void forEachKey(long parallelismThreshold, Consumer<? super K>
	 * action) { if (action == null) throw new NullPointerException(); new
	 * ForEachKeyTask<K,V> (null, batchFor(parallelismThreshold), 0, 0, table,
	 * action).invoke(); }
	 */
	/**
	 * Performs the given action for each non-null transformation of each key.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param transformer
	 *            a function returning the transformation for an element, or
	 *            null if there is no transformation (in which case the action
	 *            is not applied)
	 * @param action
	 *            the action
	 * @param <U>
	 *            the return type of the transformer
	 * @since 1.8
	 */
	/*
	 * public <U> void forEachKey(long parallelismThreshold, Function<? super K,
	 * ? extends U> transformer, Consumer<? super U> action) { if (transformer
	 * == null || action == null) throw new NullPointerException(); new
	 * ForEachTransformedKeyTask<K,V,U> (null, batchFor(parallelismThreshold),
	 * 0, 0, table, transformer, action).invoke(); }
	 */
	/**
	 * Returns a non-null result from applying the given search function on each
	 * key, or null if none. Upon success, further element processing is
	 * suppressed and the results of any other parallel invocations of the
	 * search function are ignored.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param searchFunction
	 *            a function returning a non-null result on success, else null
	 * @param <U>
	 *            the return type of the search function
	 * @return a non-null result from applying the given search function on each
	 *         key, or null if none
	 * @since 1.8
	 */
	/*
	 * public <U> U searchKeys(long parallelismThreshold, Function<? super K, ?
	 * extends U> searchFunction) { if (searchFunction == null) throw new
	 * NullPointerException(); return new SearchKeysTask<K,V,U> (null,
	 * batchFor(parallelismThreshold), 0, 0, table, searchFunction, new
	 * AtomicReference<U>()).invoke(); }
	 */
	/**
	 * Returns the result of accumulating all keys using the given reducer to
	 * combine values, or null if none.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param reducer
	 *            a commutative associative combining function
	 * @return the result of accumulating all keys using the given reducer to
	 *         combine values, or null if none
	 * @since 1.8
	 */
	/*
	 * public K reduceKeys(long parallelismThreshold, BiFunction<? super K, ?
	 * super K, ? extends K> reducer) { if (reducer == null) throw new
	 * NullPointerException(); return new ReduceKeysTask<K,V> (null,
	 * batchFor(parallelismThreshold), 0, 0, table, null, reducer).invoke(); }
	 */
	/**
	 * Returns the result of accumulating the given transformation of all keys
	 * using the given reducer to combine values, or null if none.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param transformer
	 *            a function returning the transformation for an element, or
	 *            null if there is no transformation (in which case it is not
	 *            combined)
	 * @param reducer
	 *            a commutative associative combining function
	 * @param <U>
	 *            the return type of the transformer
	 * @return the result of accumulating the given transformation of all keys
	 * @since 1.8
	 */
	/*
	 * public <U> U reduceKeys(long parallelismThreshold, Function<? super K, ?
	 * extends U> transformer, BiFunction<? super U, ? super U, ? extends U>
	 * reducer) { if (transformer == null || reducer == null) throw new
	 * NullPointerException(); return new MapReduceKeysTask<K,V,U> (null,
	 * batchFor(parallelismThreshold), 0, 0, table, null, transformer,
	 * reducer).invoke(); }
	 */
	/**
	 * Returns the result of accumulating the given transformation of all keys
	 * using the given reducer to combine values, and the given basis as an
	 * identity value.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param transformer
	 *            a function returning the transformation for an element
	 * @param basis
	 *            the identity (initial default value) for the reduction
	 * @param reducer
	 *            a commutative associative combining function
	 * @return the result of accumulating the given transformation of all keys
	 * @since 1.8
	 */
	/*
	 * public double reduceKeysToDouble(long parallelismThreshold,
	 * ToDoubleFunction<? super K> transformer, double basis,
	 * DoubleBinaryOperator reducer) { if (transformer == null || reducer ==
	 * null) throw new NullPointerException(); return new
	 * MapReduceKeysToDoubleTask<K,V> (null, batchFor(parallelismThreshold), 0,
	 * 0, table, null, transformer, basis, reducer).invoke(); }
	 */
	/**
	 * Returns the result of accumulating the given transformation of all keys
	 * using the given reducer to combine values, and the given basis as an
	 * identity value.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param transformer
	 *            a function returning the transformation for an element
	 * @param basis
	 *            the identity (initial default value) for the reduction
	 * @param reducer
	 *            a commutative associative combining function
	 * @return the result of accumulating the given transformation of all keys
	 * @since 1.8
	 */
	/*
	 * public long reduceKeysToLong(long parallelismThreshold, ToLongFunction<?
	 * super K> transformer, long basis, LongBinaryOperator reducer) { if
	 * (transformer == null || reducer == null) throw new
	 * NullPointerException(); return new MapReduceKeysToLongTask<K,V> (null,
	 * batchFor(parallelismThreshold), 0, 0, table, null, transformer, basis,
	 * reducer).invoke(); }
	 */
	/**
	 * Returns the result of accumulating the given transformation of all keys
	 * using the given reducer to combine values, and the given basis as an
	 * identity value.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param transformer
	 *            a function returning the transformation for an element
	 * @param basis
	 *            the identity (initial default value) for the reduction
	 * @param reducer
	 *            a commutative associative combining function
	 * @return the result of accumulating the given transformation of all keys
	 * @since 1.8
	 */
	/*
	 * public int reduceKeysToInt(long parallelismThreshold, ToIntFunction<?
	 * super K> transformer, int basis, IntBinaryOperator reducer) { if
	 * (transformer == null || reducer == null) throw new
	 * NullPointerException(); return new MapReduceKeysToIntTask<K,V> (null,
	 * batchFor(parallelismThreshold), 0, 0, table, null, transformer, basis,
	 * reducer).invoke(); }
	 */
	/**
	 * Performs the given action for each value.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param action
	 *            the action
	 * @since 1.8
	 */
	/*
	 * public void forEachValue(long parallelismThreshold, Consumer<? super V>
	 * action) { if (action == null) throw new NullPointerException(); new
	 * ForEachValueTask<K,V> (null, batchFor(parallelismThreshold), 0, 0, table,
	 * action).invoke(); }
	 */
	/**
	 * Performs the given action for each non-null transformation of each value.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param transformer
	 *            a function returning the transformation for an element, or
	 *            null if there is no transformation (in which case the action
	 *            is not applied)
	 * @param action
	 *            the action
	 * @param <U>
	 *            the return type of the transformer
	 * @since 1.8
	 */
	/*
	 * public <U> void forEachValue(long parallelismThreshold, Function<? super
	 * V, ? extends U> transformer, Consumer<? super U> action) { if
	 * (transformer == null || action == null) throw new NullPointerException();
	 * new ForEachTransformedValueTask<K,V,U> (null,
	 * batchFor(parallelismThreshold), 0, 0, table, transformer,
	 * action).invoke(); }
	 */

	/**
	 * Returns a non-null result from applying the given search function on each
	 * value, or null if none. Upon success, further element processing is
	 * suppressed and the results of any other parallel invocations of the
	 * search function are ignored.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param searchFunction
	 *            a function returning a non-null result on success, else null
	 * @param <U>
	 *            the return type of the search function
	 * @return a non-null result from applying the given search function on each
	 *         value, or null if none
	 * @since 1.8
	 */
	/*
	 * public <U> U searchValues(long parallelismThreshold, Function<? super V,
	 * ? extends U> searchFunction) { if (searchFunction == null) throw new
	 * NullPointerException(); return new SearchValuesTask<K,V,U> (null,
	 * batchFor(parallelismThreshold), 0, 0, table, searchFunction, new
	 * AtomicReference<U>()).invoke(); }
	 */
	/**
	 * Returns the result of accumulating all values using the given reducer to
	 * combine values, or null if none.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param reducer
	 *            a commutative associative combining function
	 * @return the result of accumulating all values
	 * @since 1.8
	 */
	/*
	 * public V reduceValues(long parallelismThreshold, BiFunction<? super V, ?
	 * super V, ? extends V> reducer) { if (reducer == null) throw new
	 * NullPointerException(); return new ReduceValuesTask<K,V> (null,
	 * batchFor(parallelismThreshold), 0, 0, table, null, reducer).invoke(); }
	 */

	/**
	 * Returns the result of accumulating the given transformation of all values
	 * using the given reducer to combine values, or null if none.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param transformer
	 *            a function returning the transformation for an element, or
	 *            null if there is no transformation (in which case it is not
	 *            combined)
	 * @param reducer
	 *            a commutative associative combining function
	 * @param <U>
	 *            the return type of the transformer
	 * @return the result of accumulating the given transformation of all values
	 * @since 1.8
	 */
	/*
	 * public <U> U reduceValues(long parallelismThreshold, Function<? super V,
	 * ? extends U> transformer, BiFunction<? super U, ? super U, ? extends U>
	 * reducer) { if (transformer == null || reducer == null) throw new
	 * NullPointerException(); return new MapReduceValuesTask<K,V,U> (null,
	 * batchFor(parallelismThreshold), 0, 0, table, null, transformer,
	 * reducer).invoke(); }
	 */

	/**
	 * Returns the result of accumulating the given transformation of all values
	 * using the given reducer to combine values, and the given basis as an
	 * identity value.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param transformer
	 *            a function returning the transformation for an element
	 * @param basis
	 *            the identity (initial default value) for the reduction
	 * @param reducer
	 *            a commutative associative combining function
	 * @return the result of accumulating the given transformation of all values
	 * @since 1.8
	 */
	/*
	 * public double reduceValuesToDouble(long parallelismThreshold,
	 * ToDoubleFunction<? super V> transformer, double basis,
	 * DoubleBinaryOperator reducer) { if (transformer == null || reducer ==
	 * null) throw new NullPointerException(); return new
	 * MapReduceValuesToDoubleTask<K,V> (null, batchFor(parallelismThreshold),
	 * 0, 0, table, null, transformer, basis, reducer).invoke(); }
	 */

	/**
	 * Returns the result of accumulating the given transformation of all values
	 * using the given reducer to combine values, and the given basis as an
	 * identity value.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param transformer
	 *            a function returning the transformation for an element
	 * @param basis
	 *            the identity (initial default value) for the reduction
	 * @param reducer
	 *            a commutative associative combining function
	 * @return the result of accumulating the given transformation of all values
	 * @since 1.8
	 */
	/*
	 * public long reduceValuesToLong(long parallelismThreshold,
	 * ToLongFunction<? super V> transformer, long basis, LongBinaryOperator
	 * reducer) { if (transformer == null || reducer == null) throw new
	 * NullPointerException(); return new MapReduceValuesToLongTask<K,V> (null,
	 * batchFor(parallelismThreshold), 0, 0, table, null, transformer, basis,
	 * reducer).invoke(); }
	 */

	/**
	 * Returns the result of accumulating the given transformation of all values
	 * using the given reducer to combine values, and the given basis as an
	 * identity value.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param transformer
	 *            a function returning the transformation for an element
	 * @param basis
	 *            the identity (initial default value) for the reduction
	 * @param reducer
	 *            a commutative associative combining function
	 * @return the result of accumulating the given transformation of all values
	 * @since 1.8
	 */
	/*
	 * public int reduceValuesToInt(long parallelismThreshold, ToIntFunction<?
	 * super V> transformer, int basis, IntBinaryOperator reducer) { if
	 * (transformer == null || reducer == null) throw new
	 * NullPointerException(); return new MapReduceValuesToIntTask<K,V> (null,
	 * batchFor(parallelismThreshold), 0, 0, table, null, transformer, basis,
	 * reducer).invoke(); }
	 */

	/**
	 * Performs the given action for each entry.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param action
	 *            the action
	 * @since 1.8
	 */
	/*
	 * public void forEachEntry(long parallelismThreshold, Consumer<? super
	 * Map.Entry> action) { if (action == null) throw new
	 * NullPointerException(); new ForEachEntryTask(null,
	 * batchFor(parallelismThreshold), 0, 0, table, action).invoke(); }
	 */

	/**
	 * Performs the given action for each non-null transformation of each entry.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param transformer
	 *            a function returning the transformation for an element, or
	 *            null if there is no transformation (in which case the action
	 *            is not applied)
	 * @param action
	 *            the action
	 * @param <U>
	 *            the return type of the transformer
	 * @since 1.8
	 */
	/*
	 * public <U> void forEachEntry(long parallelismThreshold,
	 * Function<Map.Entry, ? extends U> transformer, Consumer<? super U> action)
	 * { if (transformer == null || action == null) throw new
	 * NullPointerException(); new ForEachTransformedEntryTask<U> (null,
	 * batchFor(parallelismThreshold), 0, 0, table, transformer,
	 * action).invoke(); }
	 */

	/**
	 * Returns a non-null result from applying the given search function on each
	 * entry, or null if none. Upon success, further element processing is
	 * suppressed and the results of any other parallel invocations of the
	 * search function are ignored.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param searchFunction
	 *            a function returning a non-null result on success, else null
	 * @param <U>
	 *            the return type of the search function
	 * @return a non-null result from applying the given search function on each
	 *         entry, or null if none
	 * @since 1.8
	 */
	/*
	 * public <U> U searchEntries(long parallelismThreshold, Function<Map.Entry,
	 * ? extends U> searchFunction) { if (searchFunction == null) throw new
	 * NullPointerException(); return new SearchEntriesTask<U> (null,
	 * batchFor(parallelismThreshold), 0, 0, table, searchFunction, new
	 * AtomicReference<U>()).invoke(); }
	 */

	/**
	 * Returns the result of accumulating all entries using the given reducer to
	 * combine values, or null if none.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param reducer
	 *            a commutative associative combining function
	 * @return the result of accumulating all entries
	 * @since 1.8
	 */
	/*
	 * public Map.Entry reduceEntries(long parallelismThreshold,
	 * BiFunction<Map.Entry, Map.Entry, ? extends Map.Entry> reducer) { if
	 * (reducer == null) throw new NullPointerException(); return new
	 * ReduceEntriesTask (null, batchFor(parallelismThreshold), 0, 0, table,
	 * null, reducer).invoke(); }
	 */

	/**
	 * Returns the result of accumulating the given transformation of all
	 * entries using the given reducer to combine values, or null if none.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param transformer
	 *            a function returning the transformation for an element, or
	 *            null if there is no transformation (in which case it is not
	 *            combined)
	 * @param reducer
	 *            a commutative associative combining function
	 * @param <U>
	 *            the return type of the transformer
	 * @return the result of accumulating the given transformation of all
	 *         entries
	 * @since 1.8
	 */
	/*
	 * public <U> U reduceEntries(long parallelismThreshold, Function<Map.Entry,
	 * ? extends U> transformer, BiFunction<? super U, ? super U, ? extends U>
	 * reducer) { if (transformer == null || reducer == null) throw new
	 * NullPointerException(); return new MapReduceEntriesTask<U> (null,
	 * batchFor(parallelismThreshold), 0, 0, table, null, transformer,
	 * reducer).invoke(); }
	 */

	/**
	 * Returns the result of accumulating the given transformation of all
	 * entries using the given reducer to combine values, and the given basis as
	 * an identity value.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param transformer
	 *            a function returning the transformation for an element
	 * @param basis
	 *            the identity (initial default value) for the reduction
	 * @param reducer
	 *            a commutative associative combining function
	 * @return the result of accumulating the given transformation of all
	 *         entries
	 * @since 1.8
	 */
	/*
	 * public double reduceEntriesToDouble(long parallelismThreshold,
	 * ToDoubleFunction<Map.Entry> transformer, double basis,
	 * DoubleBinaryOperator reducer) { if (transformer == null || reducer ==
	 * null) throw new NullPointerException(); return new
	 * MapReduceEntriesToDoubleTask (null, batchFor(parallelismThreshold), 0, 0,
	 * table, null, transformer, basis, reducer).invoke(); }
	 */

	/**
	 * Returns the result of accumulating the given transformation of all
	 * entries using the given reducer to combine values, and the given basis as
	 * an identity value.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param transformer
	 *            a function returning the transformation for an element
	 * @param basis
	 *            the identity (initial default value) for the reduction
	 * @param reducer
	 *            a commutative associative combining function
	 * @return the result of accumulating the given transformation of all
	 *         entries
	 * @since 1.8
	 */
	/*
	 * public long reduceEntriesToLong(long parallelismThreshold,
	 * ToLongFunction<Map.Entry> transformer, long basis, LongBinaryOperator
	 * reducer) { if (transformer == null || reducer == null) throw new
	 * NullPointerException(); return new MapReduceEntriesToLongTask (null,
	 * batchFor(parallelismThreshold), 0, 0, table, null, transformer, basis,
	 * reducer).invoke(); }
	 */

	/**
	 * Returns the result of accumulating the given transformation of all
	 * entries using the given reducer to combine values, and the given basis as
	 * an identity value.
	 *
	 * @param parallelismThreshold
	 *            the (estimated) number of elements needed for this operation
	 *            to be executed in parallel
	 * @param transformer
	 *            a function returning the transformation for an element
	 * @param basis
	 *            the identity (initial default value) for the reduction
	 * @param reducer
	 *            a commutative associative combining function
	 * @return the result of accumulating the given transformation of all
	 *         entries
	 * @since 1.8
	 */
	/*
	 * public int reduceEntriesToInt(long parallelismThreshold,
	 * ToIntFunction<Map.Entry> transformer, int basis, IntBinaryOperator
	 * reducer) { if (transformer == null || reducer == null) throw new
	 * NullPointerException(); return new MapReduceEntriesToIntTask (null,
	 * batchFor(parallelismThreshold), 0, 0, table, null, transformer, basis,
	 * reducer).invoke(); }
	 */

	/* ----------------Views -------------- */

	/**
	 * Exported Entry for EntryIterator
	 */
	static public final class MapEntry
	{
		final long key; // non-null
		ArrayList<Object> val; // non-null
		final InternalConcurrentHashMap map;

		MapEntry(long key, ArrayList<Object> val, InternalConcurrentHashMap map)
		{
			this.key = key;
			this.val = val;
			this.map = map;
		}

		@Override
		public boolean equals(Object o)
		{
			long k;
			ArrayList<Object> v;
			if (o instanceof Node)
			{
				Node e;
				return ((k = (e = (Node)o).getKey()) != -1 && (v = e.getValue()) != null && (k == key) && (v == val || v.equals(val)));
			}
			else if (o instanceof MapEntry)
			{
				MapEntry e;
				return ((k = (e = (MapEntry)o).getKey()) != -1 && (v = e.getValue()) != null && (k == key) && (v == val || v.equals(val)));
			}
			else
			{
				return false;
			}
		}

		public long getKey()
		{
			return key;
		}

		public ArrayList<Object> getValue()
		{
			return val;
		}

		@Override
		public int hashCode()
		{
			return (int)(key ^ (key >>> 32)) ^ val.hashCode();
		}

		/**
		 * Sets our entry's value and writes through to the map. The value to
		 * return is somewhat arbitrary here. Since we do not necessarily track
		 * asynchronous changes, the most recent "previous" value could be
		 * different from what we return (or could even have been removed, in
		 * which case the put will re-establish). We do not and cannot guarantee
		 * more.
		 */
		public ArrayList<Object> setValue(ArrayList<Object> value)
		{
			if (value == null)
			{
				throw new NullPointerException();
			}
			ArrayList<Object> v = val;
			val = value;
			map.put(key, value);
			return v;
		}

		@Override
		public String toString()
		{
			return key + "=" + val;
		}
	}

	static public final class ValueIterator extends BaseIterator implements Iterator, Enumeration
	{
		ValueIterator(Node[] tab, int index, int size, int limit, InternalConcurrentHashMap map)
		{
			super(tab, index, size, limit, map);
		}

		@Override
		public final ArrayList<Object> next()
		{
			// try
			// {
			Node p;
			if ((p = next) == null)
			{
				throw new NoSuchElementException();
			}
			ArrayList<Object> v = p.val;
			lastReturned = p;
			advance();
			return v;
			// }
			// catch(Throwable e)
			// {
			// HRDBMSWorker.logger.debug("", e);
			// return null;
			// }
		}

		@Override
		public final ArrayList<Object> nextElement()
		{
			return next();
		}
	}

	/**
	 * A view of a ConcurrentHashMap as a {@link Collection} of values, in which
	 * additions are disabled. This class cannot be directly instantiated. See
	 * {@link #values()}.
	 */
	static public final class ValuesView extends CollectionView
	{
		ValuesView(InternalConcurrentHashMap map)
		{
			super(map);
		}

		public final boolean add(ArrayList<Object> e)
		{
			throw new UnsupportedOperationException();
		}

		/*
		 * public final boolean addAll(Collection<? extends V> c) { throw new
		 * UnsupportedOperationException(); }
		 */

		public final boolean contains(Object o)
		{
			return map.containsValue(o);
		}

		public final Iterator<ArrayList<Object>> iterator()
		{
			// try
			// {
			InternalConcurrentHashMap m = map;
			Node[] t;
			int f = (t = m.table) == null ? 0 : t.length;
			return new ValueIterator(t, f, 0, f, m);
			// }
			// catch(Throwable e)
			// {
			// HRDBMSWorker.logger.debug("", e);
			// return null;
			// }
		}

		public final boolean remove(Object o)
		{
			if (o != null)
			{
				for (Iterator<ArrayList<Object>> it = iterator(); it.hasNext();)
				{
					if (o.equals(it.next()))
					{
						it.remove();
						return true;
					}
				}
			}
			return false;
		}

		/*
		 * public Spliterator<ArrayList<Object>> spliterator() { Node[] t;
		 * InternalConcurrentHashMap m = map; long n = m.sumCount(); int f = (t
		 * = m.table) == null ? 0 : t.length; return new ValueSpliterator(t, f,
		 * 0, f, n < 0L ? 0L : n); }
		 */

		/*
		 * public void forEach(Consumer<? super V> action) { if (action == null)
		 * throw new NullPointerException(); Node[] t; if ((t = map.table) !=
		 * null) { Traverser it = new Traverser(t, t.length, 0, t.length); for
		 * (Node p; (p = it.advance()) != null; ) action.accept(p.val); } }
		 */
	}

	/**
	 * Base of key, value, and entry Iterators. Adds fields to Traverser to
	 * support iterator.remove.
	 */
	static class BaseIterator extends Traverser
	{
		final InternalConcurrentHashMap map;
		Node lastReturned;

		BaseIterator(Node[] tab, int size, int index, int limit, InternalConcurrentHashMap map)
		{
			super(tab, size, index, limit);
			this.map = map;
			advance();
		}

		public final boolean hasMoreElements()
		{
			return next != null;
		}

		public final boolean hasNext()
		{
			return next != null;
		}

		public final void remove()
		{
			Node p;
			if ((p = lastReturned) == null)
			{
				throw new IllegalStateException();
			}
			lastReturned = null;
			map.replaceNode(p.key, null, null);
		}
	}

	// -------------------------------------------------------

	/**
	 * Base class for views.
	 */
	abstract static class CollectionView
	{
		final InternalConcurrentHashMap map;

		CollectionView(InternalConcurrentHashMap map)
		{
			this.map = map;
		}

		/**
		 * Removes all of the elements from this view, by removing all the
		 * mappings from the map backing this view.
		 */
		public final void clear()
		{
			map.clear();
		}

		/**
		 * Returns the map backing this view.
		 *
		 * @return the map backing this view
		 */
		public InternalConcurrentHashMap getMap()
		{
			return map;
		}

		public final boolean isEmpty()
		{
			return map.isEmpty();
		}

		public final int size()
		{
			return map.size();
		}

		/*
		 * public final Object[] toArray() { long sz = map.mappingCount(); if
		 * (sz > MAX_ARRAY_SIZE) throw new OutOfMemoryError(oomeMsg); int n =
		 * (int)sz; Object[] r = new Object[n]; int i = 0; for (E e : this) { if
		 * (i == n) { if (n >= MAX_ARRAY_SIZE) throw new
		 * OutOfMemoryError(oomeMsg); if (n >= MAX_ARRAY_SIZE - (MAX_ARRAY_SIZE
		 * >>> 1) - 1) n = MAX_ARRAY_SIZE; else n += (n >>> 1) + 1; r =
		 * Arrays.copyOf(r, n); } r[i++] = e; } return (i == n) ? r :
		 * Arrays.copyOf(r, i); }
		 */

		/*
		 * @SuppressWarnings("unchecked") public final <T> T[] toArray(T[] a) {
		 * long sz = map.mappingCount(); if (sz > MAX_ARRAY_SIZE) throw new
		 * OutOfMemoryError(oomeMsg); int m = (int)sz; T[] r = (a.length >= m) ?
		 * a : (T[])java.lang.reflect.Array
		 * .newInstance(a.getClass().getComponentType(), m); int n = r.length;
		 * int i = 0; for (E e : this) { if (i == n) { if (n >= MAX_ARRAY_SIZE)
		 * throw new OutOfMemoryError(oomeMsg); if (n >= MAX_ARRAY_SIZE -
		 * (MAX_ARRAY_SIZE >>> 1) - 1) n = MAX_ARRAY_SIZE; else n += (n >>> 1) +
		 * 1; r = Arrays.copyOf(r, n); } r[i++] = (T)e; } if (a == r && i < n) {
		 * r[i] = null; // null-terminate return r; } return (i == n) ? r :
		 * Arrays.copyOf(r, i); }
		 */

		/**
		 * Returns a string representation of this collection. The string
		 * representation consists of the string representations of the
		 * collection's elements in the order they are returned by its iterator,
		 * enclosed in square brackets ({@code "[]"}). Adjacent elements are
		 * separated by the characters {@code ", "} (comma and space). Elements
		 * are converted to strings as by {@link String#valueOf(Object)}.
		 *
		 * @return a string representation of this collection
		 */
		/*
		 * public final String toString() { StringBuilder sb = new
		 * StringBuilder(); sb.append('['); Iterator<E> it = iterator(); if
		 * (it.hasNext()) { for (;;) { Object e = it.next(); sb.append(e == this
		 * ? "(this Collection)" : e); if (!it.hasNext()) break;
		 * sb.append(',').append(' '); } } return sb.append(']').toString(); }
		 */

		/*
		 * public final boolean containsAll(Collection<?> c) { if (c != this) {
		 * for (Object e : c) { if (e == null || !contains(e)) return false; } }
		 * return true; }
		 */

		/*
		 * public final boolean removeAll(Collection<?> c) { if (c == null)
		 * throw new NullPointerException(); boolean modified = false; for
		 * (Iterator<E> it = iterator(); it.hasNext();) { if
		 * (c.contains(it.next())) { it.remove(); modified = true; } } return
		 * modified; }
		 *
		 * public final boolean retainAll(Collection<?> c) { if (c == null)
		 * throw new NullPointerException(); boolean modified = false; for
		 * (Iterator<E> it = iterator(); it.hasNext();) { if
		 * (!c.contains(it.next())) { it.remove(); modified = true; } } return
		 * modified; }
		 */

	}
	/**
	 * A padded cell for distributing counts. Adapted from LongAdder and
	 * Striped64. See their internal docs for explanation.
	 */
	@sun.misc.Contended
	static final class CounterCell
	{
		volatile long value;

		CounterCell(long x)
		{
			value = x;
		}
	}
	/**
	 * A node inserted at head of bins during transfer operations.
	 */
	static final class ForwardingNode extends Node
	{
		final Node[] nextTable;

		ForwardingNode(Node[] tab)
		{
			super(MOVED, -1, null, null);
			this.nextTable = tab;
		}

		@Override
		Node find(int h, long k)
		{
			// loop to avoid arbitrarily deep recursion on forwarding nodes
			outer: for (Node[] tab = nextTable;;)
			{
				Node e;
				int n;
				if (k == -1 || tab == null || (n = tab.length) == 0 || (e = tabAt(tab, (n - 1) & h)) == null)
				{
					return null;
				}
				for (;;)
				{
					int eh;
					if ((eh = e.hash) == h && ((e.key) == k))
					{
						return e;
					}
					if (eh < 0)
					{
						if (e instanceof ForwardingNode)
						{
							tab = ((ForwardingNode)e).nextTable;
							continue outer;
						}
						else
						{
							return e.find(h, k);
						}
					}
					if ((e = e.next) == null)
					{
						return null;
					}
				}
			}
		}
	}
	/**
	 * Key-value entry. This class is never exported out as a user-mutable
	 * Map.Entry (i.e., one supporting setValue; see MapEntry below), but can be
	 * used for read-only traversals used in bulk tasks. Subclasses of Node with
	 * a negative hash field are special, and contain null keys and values (but
	 * are never exported). Otherwise, keys and vals are never null.
	 */
	static class Node
	{
		final int hash;
		final long key;
		volatile ArrayList<Object> val;
		volatile Node next;

		Node(int hash, long key, ArrayList<Object> val, Node next)
		{
			this.hash = hash;
			this.key = key;
			this.val = val;
			this.next = next;
		}

		@Override
		public final boolean equals(Object o)
		{
			long k;
			ArrayList<Object> v, u;
			if (o instanceof Node)
			{
				Node e;
				return ((k = (e = (Node)o).getKey()) != -1 && (v = e.getValue()) != null && (k == key) && (v == (u = val) || v.equals(u)));
			}
			else if (o instanceof MapEntry)
			{
				MapEntry e;
				return ((k = (e = (MapEntry)o).getKey()) != -1 && (v = e.getValue()) != null && (k == key) && (v == (u = val) || v.equals(u)));
			}
			else
			{
				return false;
			}
		}

		public final long getKey()
		{
			return key;
		}

		public final ArrayList<Object> getValue()
		{
			return val;
		}

		@Override
		public final int hashCode()
		{
			return ((int)(key ^ (key >>> 32))) ^ val.hashCode();
		}

		public final ArrayList<Object> setValue(ArrayList<Object> value)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public final String toString()
		{
			return key + "=" + val;
		}

		/**
		 * Virtualized support for map.get(); overridden in subclasses.
		 */
		Node find(int h, long k)
		{
			Node e = this;
			if (k != -1)
			{
				do
				{
					long ek;
					if (e.hash == h && ((ek = e.key) == k || (ek != -1 && k == ek)))
					{
						return e;
					}
				} while ((e = e.next) != null);
			}
			return null;
		}
	}
	/**
	 * A place-holder node used in computeIfAbsent and compute
	 */
	static final class ReservationNode extends Node
	{
		ReservationNode()
		{
			super(RESERVED, -1, null, null);
		}

		Node find(int h, Object k)
		{
			return null;
		}
	}
	/**
	 * Stripped-down version of helper class used in previous version, declared
	 * for the sake of serialization compatibility
	 */
	static class Segment extends ReentrantLock
	{
		final float loadFactor;

		Segment(float lf)
		{
			this.loadFactor = lf;
		}
	}
	/**
	 * Records the table, its length, and current traversal index for a
	 * traverser that must process a region of a forwarded table before
	 * proceeding with current table.
	 */
	static final class TableStack
	{
		int length;
		int index;
		Node[] tab;
		TableStack next;
	}
	/**
	 * Encapsulates traversal for methods such as containsValue; also serves as
	 * a base class for other iterators and spliterators.
	 *
	 * Method advance visits once each still-valid node that was reachable upon
	 * iterator construction. It might miss some that were added to a bin after
	 * the bin was visited, which is OK wrt consistency guarantees. Maintaining
	 * this property in the face of possible ongoing resizes requires a fair
	 * amount of bookkeeping state that is difficult to optimize away amidst
	 * volatile accesses. Even so, traversal maintains reasonable throughput.
	 *
	 * Normally, iteration proceeds bin-by-bin traversing lists. However, if the
	 * table has been resized, then all future steps must traverse both the bin
	 * at the current index as well as at (index + baseSize); and so on for
	 * further resizings. To paranoically cope with potential sharing by users
	 * of iterators across threads, iteration terminates if a bounds checks
	 * fails for a table read.
	 */
	static class Traverser
	{
		Node[] tab; // current table; updated if resized
		Node next; // the next entry to use
		TableStack stack, spare; // to save/restore on ForwardingNodes
		int index; // index of bin to use next
		int baseIndex; // current index of initial table
		int baseLimit; // index bound for initial table
		final int baseSize; // initial table size

		Traverser(Node[] tab, int size, int index, int limit)
		{
			this.tab = tab;
			this.baseSize = size;
			this.baseIndex = this.index = index;
			this.baseLimit = limit;
			this.next = null;
		}

		/**
		 * Saves traversal state upon encountering a forwarding node.
		 */
		private void pushState(Node[] t, int i, int n)
		{
			TableStack s = spare; // reuse if possible
			if (s != null)
			{
				spare = s.next;
			}
			else
			{
				s = new TableStack();
			}
			s.tab = t;
			s.length = n;
			s.index = i;
			s.next = stack;
			stack = s;
		}

		/**
		 * Possibly pops traversal state.
		 *
		 * @param n
		 *            length of current table
		 */
		private void recoverState(int n)
		{
			TableStack s;
			int len;
			while ((s = stack) != null && (index += (len = s.length)) >= n)
			{
				n = len;
				index = s.index;
				tab = s.tab;
				s.tab = null;
				TableStack next = s.next;
				s.next = spare; // save for reuse
				stack = next;
				spare = s;
			}
			if (s == null && (index += baseSize) >= n)
			{
				index = ++baseIndex;
			}
		}

		/**
		 * Advances if possible, returning next valid node, or null if none.
		 */
		final Node advance()
		{
			Node e;
			if ((e = next) != null)
			{
				e = e.next;
			}
			for (;;)
			{
				Node[] t;
				int i, n; // must use locals in checks
				if (e != null)
				{
					return next = e;
				}
				if (baseIndex >= baseLimit || (t = tab) == null || (n = t.length) <= (i = index) || i < 0)
				{
					return next = null;
				}
				if ((e = tabAt(t, i)) != null && e.hash < 0)
				{
					if (e instanceof ForwardingNode)
					{
						tab = ((ForwardingNode)e).nextTable;
						e = null;
						pushState(t, i, n);
						continue;
					}
					else if (e instanceof TreeBin)
					{
						e = ((TreeBin)e).first;
					}
					else
					{
						e = null;
					}
				}
				if (stack != null)
				{
					recoverState(n);
				}
				else if ((index = i + baseSize) >= n)
				{
					index = ++baseIndex; // visit upper slots if present
				}
			}
		}
	}

	/**
	 * TreeNodes used at the heads of bins. TreeBins do not hold user keys or
	 * values, but instead point to list of TreeNodes and their root. They also
	 * maintain a parasitic read-write lock forcing writers (who hold bin lock)
	 * to wait for readers (who do not) to complete before tree restructuring
	 * operations.
	 */
	static final class TreeBin extends Node
	{
		// values for lockState
		static final int WRITER = 1; // set while holding write lock
		static final int WAITER = 2; // set when waiting for write lock
		static final int READER = 4; // increment value for setting read lock
		private static final sun.misc.Unsafe U;
		private static final long LOCKSTATE;
		static
		{
			try
			{
				U = getUnsafe();
				Class<?> k = TreeBin.class;
				LOCKSTATE = U.objectFieldOffset(k.getDeclaredField("lockState"));
			}
			catch (Exception e)
			{
				throw new Error(e);
			}
		}
		TreeNode root;

		volatile TreeNode first;

		volatile Thread waiter;

		volatile int lockState;

		/**
		 * Creates bin with initial set of nodes headed by b.
		 */
		TreeBin(TreeNode b)
		{
			super(TREEBIN, -1, null, null);
			this.first = b;
			TreeNode r = null;
			for (TreeNode x = b, next; x != null; x = next)
			{
				next = (TreeNode)x.next;
				x.left = x.right = null;
				if (r == null)
				{
					x.parent = null;
					x.red = false;
					r = x;
				}
				else
				{
					long k = x.key;
					int h = x.hash;
					Class<?> kc = null;
					for (TreeNode p = r;;)
					{
						int dir, ph;
						long pk = p.key;
						if ((ph = p.hash) > h)
						{
							dir = -1;
						}
						else if (ph < h)
						{
							dir = 1;
						}
						else if ((kc == null && (kc = comparableClassFor(k)) == null) || (dir = compareComparables(kc, k, pk)) == 0)
						{
							dir = tieBreakOrder(k, pk);
						}
						TreeNode xp = p;
						if ((p = (dir <= 0) ? p.left : p.right) == null)
						{
							x.parent = xp;
							if (dir <= 0)
							{
								xp.left = x;
							}
							else
							{
								xp.right = x;
							}
							r = balanceInsertion(r, x);
							break;
						}
					}
				}
			}
			this.root = r;
			assert checkInvariants(root);
		}

		static TreeNode balanceDeletion(TreeNode root, TreeNode x)
		{
			for (TreeNode xp, xpl, xpr;;)
			{
				if (x == null || x == root)
				{
					return root;
				}
				else if ((xp = x.parent) == null)
				{
					x.red = false;
					return x;
				}
				else if (x.red)
				{
					x.red = false;
					return root;
				}
				else if ((xpl = xp.left) == x)
				{
					if ((xpr = xp.right) != null && xpr.red)
					{
						xpr.red = false;
						xp.red = true;
						root = rotateLeft(root, xp);
						xpr = (xp = x.parent) == null ? null : xp.right;
					}
					if (xpr == null)
					{
						x = xp;
					}
					else
					{
						TreeNode sl = xpr.left, sr = xpr.right;
						if ((sr == null || !sr.red) && (sl == null || !sl.red))
						{
							xpr.red = true;
							x = xp;
						}
						else
						{
							if (sr == null || !sr.red)
							{
								if (sl != null)
								{
									sl.red = false;
								}
								xpr.red = true;
								root = rotateRight(root, xpr);
								xpr = (xp = x.parent) == null ? null : xp.right;
							}
							if (xpr != null)
							{
								xpr.red = (xp == null) ? false : xp.red;
								if ((sr = xpr.right) != null)
								{
									sr.red = false;
								}
							}
							if (xp != null)
							{
								xp.red = false;
								root = rotateLeft(root, xp);
							}
							x = root;
						}
					}
				}
				else
				{ // symmetric
					if (xpl != null && xpl.red)
					{
						xpl.red = false;
						xp.red = true;
						root = rotateRight(root, xp);
						xpl = (xp = x.parent) == null ? null : xp.left;
					}
					if (xpl == null)
					{
						x = xp;
					}
					else
					{
						TreeNode sl = xpl.left, sr = xpl.right;
						if ((sl == null || !sl.red) && (sr == null || !sr.red))
						{
							xpl.red = true;
							x = xp;
						}
						else
						{
							if (sl == null || !sl.red)
							{
								if (sr != null)
								{
									sr.red = false;
								}
								xpl.red = true;
								root = rotateLeft(root, xpl);
								xpl = (xp = x.parent) == null ? null : xp.left;
							}
							if (xpl != null)
							{
								xpl.red = (xp == null) ? false : xp.red;
								if ((sl = xpl.left) != null)
								{
									sl.red = false;
								}
							}
							if (xp != null)
							{
								xp.red = false;
								root = rotateRight(root, xp);
							}
							x = root;
						}
					}
				}
			}
		}

		static TreeNode balanceInsertion(TreeNode root, TreeNode x)
		{
			x.red = true;
			for (TreeNode xp, xpp, xppl, xppr;;)
			{
				if ((xp = x.parent) == null)
				{
					x.red = false;
					return x;
				}
				else if (!xp.red || (xpp = xp.parent) == null)
				{
					return root;
				}
				if (xp == (xppl = xpp.left))
				{
					if ((xppr = xpp.right) != null && xppr.red)
					{
						xppr.red = false;
						xp.red = false;
						xpp.red = true;
						x = xpp;
					}
					else
					{
						if (x == xp.right)
						{
							root = rotateLeft(root, x = xp);
							xpp = (xp = x.parent) == null ? null : xp.parent;
						}
						if (xp != null)
						{
							xp.red = false;
							if (xpp != null)
							{
								xpp.red = true;
								root = rotateRight(root, xpp);
							}
						}
					}
				}
				else
				{
					if (xppl != null && xppl.red)
					{
						xppl.red = false;
						xp.red = false;
						xpp.red = true;
						x = xpp;
					}
					else
					{
						if (x == xp.left)
						{
							root = rotateRight(root, x = xp);
							xpp = (xp = x.parent) == null ? null : xp.parent;
						}
						if (xp != null)
						{
							xp.red = false;
							if (xpp != null)
							{
								xpp.red = true;
								root = rotateLeft(root, xpp);
							}
						}
					}
				}
			}
		}

		/**
		 * Recursive invariant check
		 */
		static boolean checkInvariants(TreeNode t)
		{
			TreeNode tp = t.parent, tl = t.left, tr = t.right, tb = t.prev, tn = (TreeNode)t.next;
			if (tb != null && tb.next != t)
			{
				return false;
			}
			if (tn != null && tn.prev != t)
			{
				return false;
			}
			if (tp != null && t != tp.left && t != tp.right)
			{
				return false;
			}
			if (tl != null && (tl.parent != t || tl.hash > t.hash))
			{
				return false;
			}
			if (tr != null && (tr.parent != t || tr.hash < t.hash))
			{
				return false;
			}
			if (t.red && tl != null && tl.red && tr != null && tr.red)
			{
				return false;
			}
			if (tl != null && !checkInvariants(tl))
			{
				return false;
			}
			if (tr != null && !checkInvariants(tr))
			{
				return false;
			}
			return true;
		}

		static TreeNode rotateLeft(TreeNode root, TreeNode p)
		{
			TreeNode r, pp, rl;
			if (p != null && (r = p.right) != null)
			{
				if ((rl = p.right = r.left) != null)
				{
					rl.parent = p;
				}
				if ((pp = r.parent = p.parent) == null)
				{
					(root = r).red = false;
				}
				else if (pp.left == p)
				{
					pp.left = r;
				}
				else
				{
					pp.right = r;
				}
				r.left = p;
				p.parent = r;
			}
			return root;
		}

		/* ------------------------------------------------------------ */
		// Red-black tree methods, all adapted from CLR

		static TreeNode rotateRight(TreeNode root, TreeNode p)
		{
			TreeNode l, pp, lr;
			if (p != null && (l = p.left) != null)
			{
				if ((lr = p.left = l.right) != null)
				{
					lr.parent = p;
				}
				if ((pp = l.parent = p.parent) == null)
				{
					(root = l).red = false;
				}
				else if (pp.right == p)
				{
					pp.right = l;
				}
				else
				{
					pp.left = l;
				}
				l.right = p;
				p.parent = l;
			}
			return root;
		}

		/**
		 * Tie-breaking utility for ordering insertions when equal hashCodes and
		 * non-comparable. We don't require a total order, just a consistent
		 * insertion rule to maintain equivalence across rebalancings.
		 * Tie-breaking further than necessary simplifies testing a bit.
		 */
		static int tieBreakOrder(Object a, Object b)
		{
			int d;
			if (a == null || b == null || (d = a.getClass().getName().compareTo(b.getClass().getName())) == 0)
			{
				d = (System.identityHashCode(a) <= System.identityHashCode(b) ? -1 : 1);
			}
			return d;
		}

		/**
		 * Possibly blocks awaiting root lock.
		 */
		private final void contendedLock()
		{
			boolean waiting = false;
			for (int s;;)
			{
				if (((s = lockState) & ~WAITER) == 0)
				{
					if (U.compareAndSwapInt(this, LOCKSTATE, s, WRITER))
					{
						if (waiting)
						{
							waiter = null;
						}
						return;
					}
				}
				else if ((s & WAITER) == 0)
				{
					if (U.compareAndSwapInt(this, LOCKSTATE, s, s | WAITER))
					{
						waiting = true;
						waiter = Thread.currentThread();
					}
				}
				else if (waiting)
				{
					LockSupport.park(this);
				}
			}
		}

		/**
		 * Acquires write lock for tree restructuring.
		 */
		private final void lockRoot()
		{
			if (!U.compareAndSwapInt(this, LOCKSTATE, 0, WRITER))
			{
				contendedLock(); // offload to separate method
			}
		}

		/**
		 * Releases write lock for tree restructuring.
		 */
		private final void unlockRoot()
		{
			lockState = 0;
		}

		/**
		 * Returns matching node or null if none. Tries to search using tree
		 * comparisons from root, but continues linear search when lock not
		 * available.
		 */
		@Override
		final Node find(int h, long k)
		{
			if (k != -1)
			{
				for (Node e = first; e != null;)
				{
					int s;
					if (((s = lockState) & (WAITER | WRITER)) != 0)
					{
						if (e.hash == h && ((e.key) == k))
						{
							return e;
						}
						e = e.next;
					}
					else if (U.compareAndSwapInt(this, LOCKSTATE, s, s + READER))
					{
						TreeNode r, p;
						try
						{
							p = ((r = root) == null ? null : r.findTreeNode(h, k, null));
						}
						finally
						{
							Thread w;
							if (U.getAndAddInt(this, LOCKSTATE, -READER) == (READER | WAITER) && (w = waiter) != null)
							{
								LockSupport.unpark(w);
							}
						}
						return p;
					}
				}
			}
			return null;
		}

		/**
		 * Finds or adds a node.
		 *
		 * @return null if added
		 */
		final TreeNode putTreeVal(int h, long k, ArrayList<Object> v)
		{
			Class<?> kc = null;
			boolean searched = false;
			for (TreeNode p = root;;)
			{
				int dir, ph;
				long pk;
				if (p == null)
				{
					first = root = new TreeNode(h, k, v, null, null);
					break;
				}
				else if ((ph = p.hash) > h)
				{
					dir = -1;
				}
				else if (ph < h)
				{
					dir = 1;
				}
				else if ((pk = p.key) == k)
				{
					return p;
				}
				else if ((kc == null && (kc = comparableClassFor(k)) == null) || (dir = compareComparables(kc, k, pk)) == 0)
				{
					if (!searched)
					{
						TreeNode q, ch;
						searched = true;
						if (((ch = p.left) != null && (q = ch.findTreeNode(h, k, kc)) != null) || ((ch = p.right) != null && (q = ch.findTreeNode(h, k, kc)) != null))
						{
							return q;
						}
					}
					dir = tieBreakOrder(k, pk);
				}

				TreeNode xp = p;
				if ((p = (dir <= 0) ? p.left : p.right) == null)
				{
					TreeNode x, f = first;
					first = x = new TreeNode(h, k, v, f, xp);
					if (f != null)
					{
						f.prev = x;
					}
					if (dir <= 0)
					{
						xp.left = x;
					}
					else
					{
						xp.right = x;
					}
					if (!xp.red)
					{
						x.red = true;
					}
					else
					{
						lockRoot();
						try
						{
							root = balanceInsertion(root, x);
						}
						finally
						{
							unlockRoot();
						}
					}
					break;
				}
			}
			assert checkInvariants(root);
			return null;
		}

		/**
		 * Removes the given node, that must be present before this call. This
		 * is messier than typical red-black deletion code because we cannot
		 * swap the contents of an interior node with a leaf successor that is
		 * pinned by "next" pointers that are accessible independently of lock.
		 * So instead we swap the tree linkages.
		 *
		 * @return true if now too small, so should be untreeified
		 */
		final boolean removeTreeNode(TreeNode p)
		{
			TreeNode next = (TreeNode)p.next;
			TreeNode pred = p.prev; // unlink traversal pointers
			TreeNode r, rl;
			if (pred == null)
			{
				first = next;
			}
			else
			{
				pred.next = next;
			}
			if (next != null)
			{
				next.prev = pred;
			}
			if (first == null)
			{
				root = null;
				return true;
			}
			if ((r = root) == null || r.right == null || // too small
					(rl = r.left) == null || rl.left == null)
			{
				return true;
			}
			lockRoot();
			try
			{
				TreeNode replacement;
				TreeNode pl = p.left;
				TreeNode pr = p.right;
				if (pl != null && pr != null)
				{
					TreeNode s = pr, sl;
					while ((sl = s.left) != null)
					{
						s = sl;
					}
					boolean c = s.red;
					s.red = p.red;
					p.red = c; // swap colors
					TreeNode sr = s.right;
					TreeNode pp = p.parent;
					if (s == pr)
					{ // p was s's direct parent
						p.parent = s;
						s.right = p;
					}
					else
					{
						TreeNode sp = s.parent;
						if ((p.parent = sp) != null)
						{
							if (s == sp.left)
							{
								sp.left = p;
							}
							else
							{
								sp.right = p;
							}
						}
						if ((s.right = pr) != null)
						{
							pr.parent = s;
						}
					}
					p.left = null;
					if ((p.right = sr) != null)
					{
						sr.parent = p;
					}
					if ((s.left = pl) != null)
					{
						pl.parent = s;
					}
					if ((s.parent = pp) == null)
					{
						r = s;
					}
					else if (p == pp.left)
					{
						pp.left = s;
					}
					else
					{
						pp.right = s;
					}
					if (sr != null)
					{
						replacement = sr;
					}
					else
					{
						replacement = p;
					}
				}
				else if (pl != null)
				{
					replacement = pl;
				}
				else if (pr != null)
				{
					replacement = pr;
				}
				else
				{
					replacement = p;
				}
				if (replacement != p)
				{
					TreeNode pp = replacement.parent = p.parent;
					if (pp == null)
					{
						r = replacement;
					}
					else if (p == pp.left)
					{
						pp.left = replacement;
					}
					else
					{
						pp.right = replacement;
					}
					p.left = p.right = p.parent = null;
				}

				root = (p.red) ? r : balanceDeletion(r, replacement);

				if (p == replacement)
				{ // detach pointers
					TreeNode pp;
					if ((pp = p.parent) != null)
					{
						if (p == pp.left)
						{
							pp.left = null;
						}
						else if (p == pp.right)
						{
							pp.right = null;
						}
						p.parent = null;
					}
				}
			}
			finally
			{
				unlockRoot();
			}
			assert checkInvariants(root);
			return false;
		}
	}

	/**
	 * Nodes for use in TreeBins
	 */
	static final class TreeNode extends Node
	{
		TreeNode parent; // red-black tree links
		TreeNode left;
		TreeNode right;
		TreeNode prev; // needed to unlink next upon deletion
		boolean red;

		TreeNode(int hash, long key, ArrayList<Object> val, Node next, TreeNode parent)
		{
			super(hash, key, val, next);
			this.parent = parent;
		}

		@Override
		Node find(int h, long k)
		{
			return findTreeNode(h, k, null);
		}

		/**
		 * Returns the TreeNode (or null if not found) for the given key
		 * starting at given root.
		 */
		final TreeNode findTreeNode(int h, long k, Class<?> kc)
		{
			if (k != -1)
			{
				TreeNode p = this;
				do
				{
					int ph, dir;
					long pk;
					TreeNode q;
					TreeNode pl = p.left, pr = p.right;
					if ((ph = p.hash) > h)
					{
						p = pl;
					}
					else if (ph < h)
					{
						p = pr;
					}
					else if ((pk = p.key) == k)
					{
						return p;
					}
					else if (pl == null)
					{
						p = pr;
					}
					else if (pr == null)
					{
						p = pl;
					}
					else if ((kc != null || (kc = comparableClassFor(k)) != null) && (dir = compareComparables(kc, k, pk)) != 0)
					{
						p = (dir < 0) ? pl : pr;
					}
					else if ((q = pr.findTreeNode(h, k, kc)) != null)
					{
						return q;
					}
					else
					{
						p = pl;
					}
				} while (p != null);
			}
			return null;
		}
	}
}
