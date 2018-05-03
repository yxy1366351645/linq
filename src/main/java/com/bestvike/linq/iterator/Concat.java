package com.bestvike.linq.iterator;

import com.bestvike.collections.generic.Array;
import com.bestvike.collections.generic.ArrayBuilder;
import com.bestvike.collections.generic.EnumerableHelpers;
import com.bestvike.collections.generic.ICollection;
import com.bestvike.collections.generic.Marker;
import com.bestvike.collections.generic.SparseArrayBuilder;
import com.bestvike.linq.IEnumerable;
import com.bestvike.linq.IEnumerator;
import com.bestvike.linq.exception.Errors;
import com.bestvike.linq.impl.partition.IIListProvider;
import com.bestvike.linq.util.ArrayUtils;
import com.bestvike.linq.util.ListUtils;
import com.bestvike.out;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by 许崇雷 on 2018-04-26.
 */
public final class Concat {
    private Concat() {
    }

    public static <TSource> IEnumerable<TSource> concat(IEnumerable<TSource> first, IEnumerable<TSource> second) {
        if (first == null)
            throw Errors.argumentNull("first");
        if (second == null)
            throw Errors.argumentNull("second");

        return first instanceof ConcatIterator
                ? ((ConcatIterator<TSource>) first).concat(second)
                : new Concat2Iterator<>(first, second);
    }


    /// <summary>
    /// Represents the concatenation of two or more <see cref="IEnumerable{TSource}"/>.
    /// </summary>
    /// <typeparam name="TSource">The type of the source enumerables.</typeparam>
    private static abstract class ConcatIterator<TSource> extends Iterator<TSource> implements IIListProvider<TSource> {
        /// <summary>
        /// The enumerator of the current source, if <see cref="MoveNext"/> has been called.
        /// </summary>
        private IEnumerator<TSource> enumerator;

        public void close() {
            if (this.enumerator != null) {
                this.enumerator.close();
                this.enumerator = null;
            }
            super.close();
        }

        /// <summary>
        /// Gets the enumerable at a logical index in this iterator.
        /// If the index is equal to the number of enumerables this iterator holds, <c>null</c> is returned.
        /// </summary>
        /// <param name="index">The logical index.</param>
        public abstract IEnumerable<TSource> getEnumerable(int index);

        /// <summary>
        /// Creates a new iterator that concatenates this iterator with an enumerable.
        /// </summary>
        /// <param name="next">The next enumerable.</param>
        public abstract ConcatIterator<TSource> concat(IEnumerable<TSource> next);

        public boolean moveNext() {
            if (this.state == 1) {
                this.enumerator = this.getEnumerable(0).enumerator();
                this.state = 2;
            }

            if (this.state > 1) {
                while (true) {
                    if (this.enumerator.moveNext()) {
                        this.current = this.enumerator.current();
                        return true;
                    }

                    IEnumerable<TSource> next = this.getEnumerable(this.state++ - 1);
                    if (next != null) {
                        this.enumerator.close();
                        this.enumerator = next.enumerator();
                        continue;
                    }

                    this.close();
                    break;
                }
            }

            return false;
        }

        public abstract int _getCount(boolean onlyIfCheap);

        public abstract TSource[] _toArray(Class<TSource> clazz);

        public List<TSource> _toList() {
            int count = this._getCount(true);
            List<TSource> list = count != -1 ? new ArrayList<>(count) : new ArrayList<>();

            for (int i = 0; ; i++) {
                IEnumerable<TSource> source = this.getEnumerable(i);
                if (source == null)
                    break;

                ListUtils.addRange(list, source);
            }

            return list;
        }
    }

    /// <summary>
    /// Represents the concatenation of two <see cref="IEnumerable{TSource}"/>.
    /// </summary>
    /// <typeparam name="TSource">The type of the source enumerables.</typeparam>
    private static final class Concat2Iterator<TSource> extends ConcatIterator<TSource> {
        /// <summary>
        /// The first source to concatenate.
        /// </summary>
        private final IEnumerable<TSource> first;

        /// <summary>
        /// The second source to concatenate.
        /// </summary>
        private final IEnumerable<TSource> second;

        /// <summary>
        /// Initializes a new instance of the <see cref="Concat2Iterator{TSource}"/> class.
        /// </summary>
        /// <param name="first">The first source to concatenate.</param>
        /// <param name="second">The second source to concatenate.</param>
        Concat2Iterator(IEnumerable<TSource> first, IEnumerable<TSource> second) {
            assert first != null;
            assert second != null;

            this.first = first;
            this.second = second;
        }

        public Iterator<TSource> clone() {
            return new Concat2Iterator<>(this.first, this.second);
        }

        public ConcatIterator<TSource> concat(IEnumerable<TSource> next) {
            boolean hasOnlyCollections = next instanceof ICollection &&
                    this.first instanceof ICollection &&
                    this.second instanceof ICollection;
            return new ConcatNIterator<>(this, next, 2, hasOnlyCollections);
        }

        public int _getCount(boolean onlyIfCheap) {
            out<Integer> firstCountRef = out.init();
            out<Integer> secondCountRef = out.init();
            if (!EnumerableHelpers.tryGetCount(this.first, firstCountRef)) {
                if (onlyIfCheap)
                    return -1;
                firstCountRef.setValue(this.first.count());
            }

            if (!EnumerableHelpers.tryGetCount(this.second, secondCountRef)) {
                if (onlyIfCheap)
                    return -1;
                secondCountRef.setValue(this.second.count());
            }

            return Math.addExact(firstCountRef.getValue(), secondCountRef.getValue());
        }

        public IEnumerable<TSource> getEnumerable(int index) {
            assert index >= 0 && index <= 2;

            switch (index) {
                case 0:
                    return this.first;
                case 1:
                    return this.second;
                default:
                    return null;
            }
        }

        public TSource[] _toArray(Class<TSource> clazz) {
            SparseArrayBuilder<TSource> builder = new SparseArrayBuilder<>();

            boolean reservedFirst = builder.reserveOrAdd(this.first);
            boolean reservedSecond = builder.reserveOrAdd(this.second);

            TSource[] array = builder.toArray(clazz);
            if (reservedFirst) {
                Marker marker = builder.getMarkers().first();
                assert marker.getIndex() == 0;
                EnumerableHelpers.copy(this.first, array, 0, marker.getCount());
            }
            if (reservedSecond) {
                Marker marker = builder.getMarkers().last();
                EnumerableHelpers.copy(this.second, array, marker.getIndex(), marker.getCount());
            }

            return array;
        }

        @Override
        public Array<TSource> _toArray() {
            SparseArrayBuilder<TSource> builder = new SparseArrayBuilder<>();

            boolean reservedFirst = builder.reserveOrAdd(this.first);
            boolean reservedSecond = builder.reserveOrAdd(this.second);

            Array<TSource> array = builder.toArray();
            if (reservedFirst) {
                Marker marker = builder.getMarkers().first();
                assert marker.getIndex() == 0;
                EnumerableHelpers.copy(this.first, array, 0, marker.getCount());
            }

            if (reservedSecond) {
                Marker marker = builder.getMarkers().last();
                EnumerableHelpers.copy(this.second, array, marker.getIndex(), marker.getCount());
            }

            return array;
        }
    }

    /// <summary>
    /// Represents the concatenation of three or more <see cref="IEnumerable{TSource}"/>.
    /// </summary>
    /// <typeparam name="TSource">The type of the source enumerables.</typeparam>
    /// <remarks>
    /// To handle chains of >= 3 sources, we chain the <see cref="Concat"/> iterators together and allow
    /// <see cref="GetEnumerable"/> to fetch enumerables from the previous sources.  This means that rather
    /// than each <see cref="IEnumerator{T}.MoveNext"/> and <see cref="IEnumerator{T}.Current"/> calls having to traverse all of the previous
    /// sources, we only have to traverse all of the previous sources once per chained enumerable.  An alternative
    /// would be to use an array to store all of the enumerables, but this has a much better memory profile and
    /// without much additional run-time cost.
    /// </remarks>
    private static final class ConcatNIterator<TSource> extends ConcatIterator<TSource> {
        /// <summary>
        /// The linked list of previous sources.
        /// </summary>
        private final ConcatIterator<TSource> tail;

        /// <summary>
        /// The source associated with this iterator.
        /// </summary>
        private final IEnumerable<TSource> head;

        /// <summary>
        /// The logical index associated with this iterator.
        /// </summary>
        private final int headIndex;

        /// <summary>
        /// <c>true</c> if all sources this iterator concatenates implement <see cref="ICollection{TSource}"/>;
        /// otherwise, <c>false</c>.
        /// </summary>
        /// <remarks>
        /// This flag allows us to determine in O(1) time whether we can preallocate for <see cref="ToArray"/>
        /// and <see cref="ConcatIterator{TSource}.ToList"/>, and whether we can get the count of the iterator cheaply.
        /// </remarks>
        private final boolean hasOnlyCollections;

        /// <summary>
        /// Initializes a new instance of the <see cref="ConcatNIterator{TSource}"/> class.
        /// </summary>
        /// <param name="tail">The linked list of previous sources.</param>
        /// <param name="head">The source associated with this iterator.</param>
        /// <param name="headIndex">The logical index associated with this iterator.</param>
        /// <param name="hasOnlyCollections">
        /// <c>true</c> if all sources this iterator concatenates implement <see cref="ICollection{TSource}"/>;
        /// otherwise, <c>false</c>.
        /// </param>
        ConcatNIterator(ConcatIterator<TSource> tail, IEnumerable<TSource> head, int headIndex, boolean hasOnlyCollections) {
            assert tail != null;
            assert head != null;
            assert headIndex >= 2;

            this.tail = tail;
            this.head = head;
            this.headIndex = headIndex;
            this.hasOnlyCollections = hasOnlyCollections;
        }

        private ConcatNIterator<TSource> getPreviousN() {
            return this.tail instanceof ConcatNIterator ? (ConcatNIterator<TSource>) this.tail : null;
        }

        public Iterator<TSource> clone() {
            return new ConcatNIterator<>(this.tail, this.head, this.headIndex, this.hasOnlyCollections);
        }

        public ConcatIterator<TSource> concat(IEnumerable<TSource> next) {
            if (this.headIndex == Integer.MAX_VALUE - 2) {
                // In the unlikely case of this many concatenations, if we produced a ConcatNIterator
                // with int.MaxValue then state would overflow before it matched its index.
                // So we use the naïve approach of just having a left and right sequence.
                return new Concat2Iterator<>(this, next);
            }

            boolean hasOnlyCollections = this.hasOnlyCollections && next instanceof ICollection;
            return new ConcatNIterator<>(this, next, this.headIndex + 1, hasOnlyCollections);
        }

        public int _getCount(boolean onlyIfCheap) {
            if (onlyIfCheap && !this.hasOnlyCollections) {
                return -1;
            }

            int count = 0;
            ConcatNIterator<TSource> node, previousN = this;

            do {
                node = previousN;
                IEnumerable<TSource> source = node.head;

                // Enumerable.Count() handles ICollections in O(1) time, but check for them here anyway
                // to avoid a method call because 1) they're common and 2) this code is run in a loop.
                ICollection<TSource> collection = source instanceof ICollection ? ((ICollection<TSource>) source) : null;
                assert !this.hasOnlyCollections || collection != null;
                int sourceCount = collection == null ? source.count() : collection._getCount();

                count = Math.addExact(count, sourceCount);
            }
            while ((previousN = node.getPreviousN()) != null);

            assert node.tail instanceof Concat2Iterator;
            return Math.addExact(count, node.tail._getCount(onlyIfCheap));
        }

        public IEnumerable<TSource> getEnumerable(int index) {
            assert index >= 0;

            if (index > this.headIndex)
                return null;

            ConcatNIterator<TSource> node, previousN = this;
            do {
                node = previousN;
                if (index == node.headIndex)
                    return node.head;
            }
            while ((previousN = node.getPreviousN()) != null);

            assert index == 0 || index == 1;
            assert node.tail instanceof Concat2Iterator;
            return node.tail.getEnumerable(index);
        }

        public TSource[] _toArray(Class<TSource> clazz) {
            return this.hasOnlyCollections ? this.preallocatingToArray(clazz) : this.lazyToArray(clazz);
        }

        @Override
        public Array<TSource> _toArray() {
            return this.hasOnlyCollections ? this.preallocatingToArray() : this.lazyToArray();
        }

        private TSource[] lazyToArray(Class<TSource> clazz) {
            assert !this.hasOnlyCollections;

            SparseArrayBuilder<TSource> builder = new SparseArrayBuilder<>();
            ArrayBuilder<Integer> deferredCopies = new ArrayBuilder<>();

            for (int i = 0; ; i++) {
                // Unfortunately, we can't escape re-walking the linked list for each source, which has
                // quadratic behavior, because we need to add the sources in order.
                // On the bright side, the bottleneck will usually be iterating, buffering, and copying
                // each of the enumerables, so this shouldn't be a noticeable perf hit for most scenarios.

                IEnumerable<TSource> source = this.getEnumerable(i);
                if (source == null)
                    break;

                if (builder.reserveOrAdd(source))
                    deferredCopies.add(i);
            }

            TSource[] array = builder.toArray(clazz);

            ArrayBuilder<Marker> markers = builder.getMarkers();
            for (int i = 0; i < markers.getCount(); i++) {
                Marker marker = markers.get(i);
                IEnumerable<TSource> source = this.getEnumerable(deferredCopies.get(i));
                EnumerableHelpers.copy(source, array, marker.getIndex(), marker.getCount());
            }

            return array;
        }

        private Array<TSource> lazyToArray() {
            assert !this.hasOnlyCollections;

            SparseArrayBuilder<TSource> builder = new SparseArrayBuilder<>();
            ArrayBuilder<Integer> deferredCopies = new ArrayBuilder<>();

            for (int i = 0; ; i++) {
                // Unfortunately, we can't escape re-walking the linked list for each source, which has
                // quadratic behavior, because we need to add the sources in order.
                // On the bright side, the bottleneck will usually be iterating, buffering, and copying
                // each of the enumerables, so this shouldn't be a noticeable perf hit for most scenarios.

                IEnumerable<TSource> source = this.getEnumerable(i);
                if (source == null)
                    break;

                if (builder.reserveOrAdd(source))
                    deferredCopies.add(i);
            }

            Array<TSource> array = builder.toArray();

            ArrayBuilder<Marker> markers = builder.getMarkers();
            for (int i = 0; i < markers.getCount(); i++) {
                Marker marker = markers.get(i);
                IEnumerable<TSource> source = this.getEnumerable(deferredCopies.get(i));
                EnumerableHelpers.copy(source, array, marker.getIndex(), marker.getCount());
            }

            return array;
        }

        private TSource[] preallocatingToArray(Class<TSource> clazz) {
            // If there are only ICollections in this iterator, then we can just get the count, preallocate the
            // array, and copy them as we go. This has better time complexity than continuously re-walking the
            // linked list via GetEnumerable, and better memory usage than buffering the collections.

            assert this.hasOnlyCollections;

            int count = this._getCount(true);
            assert count >= 0;

            if (count == 0)
                return ArrayUtils.empty(clazz);

            TSource[] array = ArrayUtils.newInstance(clazz, count);
            int arrayIndex = array.length; // We start copying in collection-sized chunks from the end of the array.

            ConcatNIterator<TSource> node, previousN = this;
            do {
                node = previousN;
                ICollection<TSource> source = (ICollection<TSource>) node.head;
                int sourceCount = source._getCount();
                if (sourceCount > 0) {
                    arrayIndex = Math.subtractExact(arrayIndex, sourceCount);
                    source._copyTo(array, arrayIndex);
                }
            }
            while ((previousN = node.getPreviousN()) != null);

            Concat2Iterator<TSource> previous2 = (Concat2Iterator<TSource>) node.tail;
            ICollection<TSource> second = (ICollection<TSource>) previous2.second;
            int secondCount = second._getCount();

            if (secondCount > 0)
                second._copyTo(array, Math.subtractExact(arrayIndex, secondCount));

            if (arrayIndex > secondCount) {
                ICollection<TSource> first = (ICollection<TSource>) previous2.first;
                first._copyTo(array, 0);
            }

            return array;
        }

        private Array<TSource> preallocatingToArray() {
            // If there are only ICollections in this iterator, then we can just get the count, preallocate the
            // array, and copy them as we go. This has better time complexity than continuously re-walking the
            // linked list via GetEnumerable, and better memory usage than buffering the collections.

            assert this.hasOnlyCollections;

            int count = this._getCount(true);
            assert count >= 0;

            if (count == 0)
                return Array.empty();

            Array<TSource> array = Array.create(count);
            int arrayIndex = array.length(); // We start copying in collection-sized chunks from the end of the array.

            ConcatNIterator<TSource> node, previousN = this;
            do {
                node = previousN;
                ICollection<TSource> source = (ICollection<TSource>) node.head;
                int sourceCount = source._getCount();
                if (sourceCount > 0) {
                    arrayIndex = Math.subtractExact(arrayIndex, sourceCount);
                    source._copyTo(array, arrayIndex);
                }
            }
            while ((previousN = node.getPreviousN()) != null);

            Concat2Iterator<TSource> previous2 = (Concat2Iterator<TSource>) node.tail;
            ICollection<TSource> second = (ICollection<TSource>) previous2.second;
            int secondCount = second._getCount();

            if (secondCount > 0)
                second._copyTo(array, Math.subtractExact(arrayIndex, secondCount));

            if (arrayIndex > secondCount) {
                ICollection<TSource> first = (ICollection<TSource>) previous2.first;
                first._copyTo(array, 0);
            }

            return array;
        }
    }
}