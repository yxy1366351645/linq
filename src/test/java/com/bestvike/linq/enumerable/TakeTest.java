package com.bestvike.linq.enumerable;

import com.bestvike.TestCase;
import com.bestvike.linq.IEnumerable;
import com.bestvike.linq.IEnumerator;
import com.bestvike.linq.Linq;
import com.bestvike.linq.entity.Department;
import com.bestvike.linq.exception.ArgumentOutOfRangeException;
import com.bestvike.linq.exception.InvalidOperationException;
import com.bestvike.linq.util.ArgsList;
import com.bestvike.linq.util.ArrayUtils;
import com.bestvike.ref;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by 许崇雷 on 2018-05-10.
 */
class TakeTest extends TestCase {
    private static <T> IEnumerable<T> GuaranteeNotIList(IEnumerable<T> source) {
        return source.select(x -> x);
    }

    private static IEnumerable<Object[]> LazySkipAllTakenForLargeNumbers_TestData() {
        ArgsList argsList = new ArgsList();
        argsList.add(1000);
        argsList.add(1000000);
        argsList.add(Integer.MAX_VALUE);
        return argsList;
    }

    private static IEnumerable<Object[]> CountOfLazySkipTakeChain_TestData() {
        ArgsList argsList = new ArgsList();
        argsList.add(0, 0, 0);
        argsList.add(1, 1, 1);
        argsList.add(0, Integer.MAX_VALUE, 100);
        argsList.add(Integer.MAX_VALUE, 0, 0);
        argsList.add(0xffff, 1, 0);
        argsList.add(1, 0xffff, 99);
        argsList.add(Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
        argsList.add(1, Integer.MAX_VALUE, 99); // Regression test: The max index is precisely Integer.MAX_VALUE.
        argsList.add(0, 100, 100);
        argsList.add(10, 100, 90);
        return argsList;
    }

    private static IEnumerable<Object[]> FirstAndLastOfLazySkipTakeChain_TestData() {
        ArgsList argsList = new ArgsList();
        argsList.add(Linq.of(new int[]{1, 2, 3, 4}), 1, 3, 2, 4);
        argsList.add(Linq.of(new int[]{1}), 0, 1, 1, 1);
        argsList.add(Linq.of(new int[]{1, 2, 3, 5, 8, 13}), 1, Integer.MAX_VALUE, 2, 13); // Regression test: The max index is precisely Integer.MAX_VALUE.
        argsList.add(Linq.of(new int[]{1, 2, 3, 5, 8, 13}), 0, 2, 1, 2);
        argsList.add(Linq.of(new int[]{1, 2, 3, 5, 8, 13}), 500, 2, null, null);
        argsList.add(Linq.of(new int[]{}), 10, 8, null, null);
        return argsList;
    }

    private static IEnumerable<Object[]> ElementAtOfLazySkipTakeChain_TestData() {
        ArgsList argsList = new ArgsList();
        argsList.add(Linq.of(new int[]{1, 2, 3, 4, 5}), 1, 3, new int[]{-1, 0, 1, 2}, new Integer[]{null, 2, 3, 4});
        argsList.add(Linq.of(new int[]{0xfefe, 7000, 123}), 0, 3, new int[]{-1, 0, 1, 2}, new Integer[]{null, 0xfefe, 7000, 123});
        argsList.add(Linq.of(new int[]{0xfefe}), 100, 100, new int[]{-1, 0, 1, 2}, new Integer[]{null, null, null, null});
        argsList.add(Linq.of(new int[]{0xfefe, 123, 456, 7890, 5555, 55}), 1, 10, new int[]{-1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, new Integer[]{null, 123, 456, 7890, 5555, 55, null, null, null, null, null, null, null});
        return argsList;
    }

    private static IEnumerable<Object[]> DisposeSource_TestData() {
        ArgsList argsList = new ArgsList();
        argsList.add(0, -1);
        argsList.add(0, 0);
        argsList.add(1, 0);
        argsList.add(2, 1);
        argsList.add(2, 2);
        argsList.add(2, 3);
        return argsList;
    }

    @Test
    void SameResultsRepeatCallsIntQuery() {
        IEnumerable<Integer> q = Linq.of(new int[]{9999, 0, 888, -1, 66, -777, 1, 2, -12345}).where(x -> x > Integer.MIN_VALUE);

        assertEquals(q.take(9), q.take(9));
    }

    @Test
    void SameResultsRepeatCallsIntQueryIList() {
        List<Integer> q = Linq.of(new int[]{9999, 0, 888, -1, 66, -777, 1, 2, -12345}).where(x -> x > Integer.MIN_VALUE).toList();

        assertEquals(Linq.of(q).take(9), Linq.of(q).take(9));
    }

    @Test
    void SameResultsRepeatCallsStringQuery() {
        IEnumerable<String> q = Linq.of(new String[]{"!@#$%^", "C", "AAA", "", "Calling Twice", "SoS", Empty}).where(x -> !IsNullOrEmpty(x));

        assertEquals(q.take(7), q.take(7));
    }

    @Test
    void SameResultsRepeatCallsStringQueryIList() {
        List<String> q = Linq.of(new String[]{"!@#$%^", "C", "AAA", "", "Calling Twice", "SoS", Empty}).where(x -> !IsNullOrEmpty(x)).toList();

        assertEquals(Linq.of(q).take(7), Linq.of(q).take(7));
    }

    @Test
    void SourceEmptyCountPositive() {
        int[] source = {};
        assertEmpty(Linq.of(source).take(5));
    }

    @Test
    void SourceEmptyCountPositiveNotIList() {
        IEnumerable<Integer> source = NumberRangeGuaranteedNotCollectionType(0, 0);
        assertEmpty(source.take(5));
    }

    @Test
    void SourceNonEmptyCountNegative() {
        int[] source = {2, 5, 9, 1};
        assertEmpty(Linq.of(source).take(-5));
    }

    @Test
    void SourceNonEmptyCountNegativeNotIList() {
        IEnumerable<Integer> source = GuaranteeNotIList(Linq.of(new int[]{2, 5, 9, 1}));
        assertEmpty(source.take(-5));
    }

    @Test
    void SourceNonEmptyCountZero() {
        int[] source = {2, 5, 9, 1};
        assertEmpty(Linq.of(source).take(0));
    }

    @Test
    void SourceNonEmptyCountZeroNotIList() {
        IEnumerable<Integer> source = GuaranteeNotIList(Linq.of(new int[]{2, 5, 9, 1}));
        assertEmpty(source.take(0));
    }

    @Test
    void SourceNonEmptyCountOne() {
        int[] source = {2, 5, 9, 1};
        int[] expected = {2};

        assertEquals(Linq.of(expected), Linq.of(source).take(1));
    }

    @Test
    void SourceNonEmptyCountOneNotIList() {
        IEnumerable<Integer> source = GuaranteeNotIList(Linq.of(new int[]{2, 5, 9, 1}));
        int[] expected = {2};

        assertEquals(Linq.of(expected), source.take(1));
    }

    @Test
    void SourceNonEmptyTakeAllExactly() {
        int[] source = {2, 5, 9, 1};

        assertEquals(Linq.of(source), Linq.of(source).take(source.length));
    }

    @Test
    void SourceNonEmptyTakeAllExactlyNotIList() {
        IEnumerable<Integer> source = GuaranteeNotIList(Linq.of(new int[]{2, 5, 9, 1}));

        assertEquals(source, source.take(source.count()));
    }

    @Test
    void SourceNonEmptyTakeAllButOne() {
        int[] source = {2, 5, 9, 1};
        int[] expected = {2, 5, 9};

        assertEquals(Linq.of(expected), Linq.of(source).take(3));
    }

    @Test
    void RunOnce() {
        int[] source = {2, 5, 9, 1};
        int[] expected = {2, 5, 9};

        assertEquals(Linq.of(expected), Linq.of(source).runOnce().take(3));
    }

    @Test
    void SourceNonEmptyTakeAllButOneNotIList() {
        IEnumerable<Integer> source = GuaranteeNotIList(Linq.of(new int[]{2, 5, 9, 1}));
        int[] expected = {2, 5, 9};

        assertEquals(Linq.of(expected), source.take(3));
    }

    @Test
    void SourceNonEmptyTakeExcessive() {
        Integer[] source = {2, 5, null, 9, 1};

        assertEquals(Linq.of(source), Linq.of(source).take(source.length + 1));
    }

    @Test
    void SourceNonEmptyTakeExcessiveNotIList() {
        IEnumerable<Integer> source = GuaranteeNotIList(Linq.of(2, 5, null, 9, 1));

        assertEquals(source, source.take(source.count() + 1));
    }

    @Test
    void ThrowsOnNullSource() {
        IEnumerable<Integer> source = null;
        assertThrows(NullPointerException.class, () -> source.take(5));
    }

    @Test
    void ForcedToEnumeratorDoesntEnumerate() {
        IEnumerable<Integer> iterator = NumberRangeGuaranteedNotCollectionType(0, 3).take(2);
        // Don't insist on this behaviour, but check it's correct if it happens
        IEnumerator<Integer> en = (IEnumerator<Integer>) iterator;
        assertFalse(en != null && en.moveNext());
    }

    @Test
    void Count() {
        assertEquals(2, NumberRangeGuaranteedNotCollectionType(0, 3).take(2).count());
        assertEquals(2, Linq.of(new int[]{1, 2, 3}).take(2).count());
        assertEquals(0, NumberRangeGuaranteedNotCollectionType(0, 3).take(0).count());
    }

    @Test
    void ForcedToEnumeratorDoesntEnumerateIList() {
        IEnumerable<Integer> iterator = Linq.of(NumberRangeGuaranteedNotCollectionType(0, 3).toList()).take(2);
        // Don't insist on this behaviour, but check it's correct if it happens
        IEnumerator<Integer> en = (IEnumerator<Integer>) iterator;
        assertFalse(en != null && en.moveNext());
    }

    @Test
    void FollowWithTake() {
        int[] source = new int[]{5, 6, 7, 8};
        int[] expected = new int[]{5, 6};
        assertEquals(Linq.of(expected), Linq.of(source).take(5).take(3).take(2).take(40));
    }

    @Test
    void FollowWithTakeNotIList() {
        IEnumerable<Integer> source = NumberRangeGuaranteedNotCollectionType(5, 4);
        int[] expected = new int[]{5, 6};
        assertEquals(Linq.of(expected), source.take(5).take(3).take(2));
    }

    @Test
    void FollowWithSkip() {
        int[] source = new int[]{1, 2, 3, 4, 5, 6};
        int[] expected = new int[]{3, 4, 5};
        assertEquals(Linq.of(expected), Linq.of(source).take(5).skip(2).skip(-4));
    }

    @Test
    void FollowWithSkipNotIList() {
        IEnumerable<Integer> source = NumberRangeGuaranteedNotCollectionType(1, 6);
        int[] expected = new int[]{3, 4, 5};
        assertEquals(Linq.of(expected), source.take(5).skip(2).skip(-4));
    }

    @Test
    void ElementAt() {
        int[] source = new int[]{1, 2, 3, 4, 5, 6};
        IEnumerable<Integer> taken = Linq.of(source).take(3);
        assertEquals(1, taken.elementAt(0));
        assertEquals(3, taken.elementAt(2));
        assertThrows(ArgumentOutOfRangeException.class, () -> taken.elementAt(-1));
        assertThrows(ArgumentOutOfRangeException.class, () -> taken.elementAt(3));
    }

    @Test
    void ElementAtNotIList() {
        IEnumerable<Integer> source = GuaranteeNotIList(Linq.of(new int[]{1, 2, 3, 4, 5, 6}));
        IEnumerable<Integer> taken = source.take(3);
        assertEquals(1, taken.elementAt(0));
        assertEquals(3, taken.elementAt(2));
        assertThrows(ArgumentOutOfRangeException.class, () -> taken.elementAt(-1));
        assertThrows(ArgumentOutOfRangeException.class, () -> taken.elementAt(3));
    }

    @Test
    void ElementAtOrDefault() {
        int[] source = new int[]{1, 2, 3, 4, 5, 6};
        IEnumerable<Integer> taken = Linq.of(source).take(3);
        assertEquals(1, taken.elementAtOrDefault(0));
        assertEquals(3, taken.elementAtOrDefault(2));
        assertEquals(null, taken.elementAtOrDefault(-1));
        assertEquals(null, taken.elementAtOrDefault(3));
    }

    @Test
    void ElementAtOrDefaultNotIList() {
        IEnumerable<Integer> source = GuaranteeNotIList(Linq.of(new int[]{1, 2, 3, 4, 5, 6}));
        IEnumerable<Integer> taken = source.take(3);
        assertEquals(1, taken.elementAtOrDefault(0));
        assertEquals(3, taken.elementAtOrDefault(2));
        assertEquals(null, taken.elementAtOrDefault(-1));
        assertEquals(null, taken.elementAtOrDefault(3));
    }

    @Test
    void First() {
        int[] source = new int[]{1, 2, 3, 4, 5};
        assertEquals(1, Linq.of(source).take(1).first());
        assertEquals(1, Linq.of(source).take(4).first());
        assertEquals(1, Linq.of(source).take(40).first());
        assertThrows(InvalidOperationException.class, () -> Linq.of(source).take(0).first());
        assertThrows(InvalidOperationException.class, () -> Linq.of(source).skip(5).take(10).first());
    }

    @Test
    void FirstNotIList() {
        IEnumerable<Integer> source = GuaranteeNotIList(Linq.of(new int[]{1, 2, 3, 4, 5}));
        assertEquals(1, source.take(1).first());
        assertEquals(1, source.take(4).first());
        assertEquals(1, source.take(40).first());
        assertThrows(InvalidOperationException.class, () -> source.take(0).first());
        assertThrows(InvalidOperationException.class, () -> source.skip(5).take(10).first());
    }

    @Test
    void FirstOrDefault() {
        int[] source = new int[]{1, 2, 3, 4, 5};
        assertEquals(1, Linq.of(source).take(1).firstOrDefault());
        assertEquals(1, Linq.of(source).take(4).firstOrDefault());
        assertEquals(1, Linq.of(source).take(40).firstOrDefault());
        assertEquals(null, Linq.of(source).take(0).firstOrDefault());
        assertEquals(null, Linq.of(source).skip(5).take(10).firstOrDefault());
    }

    @Test
    void FirstOrDefaultNotIList() {
        IEnumerable<Integer> source = GuaranteeNotIList(Linq.of(new int[]{1, 2, 3, 4, 5}));
        assertEquals(1, source.take(1).firstOrDefault());
        assertEquals(1, source.take(4).firstOrDefault());
        assertEquals(1, source.take(40).firstOrDefault());
        assertEquals(null, source.take(0).firstOrDefault());
        assertEquals(null, source.skip(5).take(10).firstOrDefault());
    }

    @Test
    void Last() {
        int[] source = new int[]{1, 2, 3, 4, 5};
        assertEquals(1, Linq.of(source).take(1).last());
        assertEquals(5, Linq.of(source).take(5).last());
        assertEquals(5, Linq.of(source).take(40).last());
        assertThrows(InvalidOperationException.class, () -> Linq.of(source).take(0).last());
        assertThrows(InvalidOperationException.class, () -> Linq.empty().take(40).last());
    }

    @Test
    void LastNotIList() {
        IEnumerable<Integer> source = GuaranteeNotIList(Linq.of(new int[]{1, 2, 3, 4, 5}));
        assertEquals(1, source.take(1).last());
        assertEquals(5, source.take(5).last());
        assertEquals(5, source.take(40).last());
        assertThrows(InvalidOperationException.class, () -> source.take(0).last());
        assertThrows(InvalidOperationException.class, () -> GuaranteeNotIList(Linq.empty()).take(40).last());
    }

    @Test
    void LastOrDefault() {
        int[] source = new int[]{1, 2, 3, 4, 5};
        assertEquals(1, Linq.of(source).take(1).lastOrDefault());
        assertEquals(5, Linq.of(source).take(5).lastOrDefault());
        assertEquals(5, Linq.of(source).take(40).lastOrDefault());
        assertEquals(null, Linq.of(source).take(0).lastOrDefault());
        assertEquals(null, Linq.empty().take(40).lastOrDefault());
    }

    @Test
    void LastOrDefaultNotIList() {
        IEnumerable<Integer> source = GuaranteeNotIList(Linq.of(new int[]{1, 2, 3, 4, 5}));
        assertEquals(1, source.take(1).lastOrDefault());
        assertEquals(5, source.take(5).lastOrDefault());
        assertEquals(5, source.take(40).lastOrDefault());
        assertEquals(null, source.take(0).lastOrDefault());
        assertEquals(null, GuaranteeNotIList(Linq.empty()).take(40).lastOrDefault());
    }

    @Test
    void ToArray() {
        int[] source = new int[]{1, 2, 3, 4, 5};
        assertEquals(Linq.of(new int[]{1, 2, 3, 4, 5}), Linq.of(source).take(5).toArray());
        assertEquals(Linq.of(new int[]{1, 2, 3, 4, 5}), Linq.of(source).take(6).toArray());
        assertEquals(Linq.of(new int[]{1, 2, 3, 4, 5}), Linq.of(source).take(40).toArray());
        assertEquals(Linq.of(new int[]{1, 2, 3, 4}), Linq.of(source).take(4).toArray());
        assertEquals(1, Linq.of(source).take(1).toArray().single());
        assertEmpty(Linq.of(source).take(0).toArray());
        assertEmpty(Linq.of(source).take(-10).toArray());
    }

    @Test
    void ToArrayNotList() {
        IEnumerable<Integer> source = GuaranteeNotIList(Linq.of(new int[]{1, 2, 3, 4, 5}));
        assertEquals(Linq.of(new int[]{1, 2, 3, 4, 5}), source.take(5).toArray());
        assertEquals(Linq.of(new int[]{1, 2, 3, 4, 5}), source.take(6).toArray());
        assertEquals(Linq.of(new int[]{1, 2, 3, 4, 5}), source.take(40).toArray());
        assertEquals(Linq.of(new int[]{1, 2, 3, 4}), source.take(4).toArray());
        assertEquals(1, source.take(1).toArray().single());
        assertEmpty(source.take(0).toArray());
        assertEmpty(source.take(-10).toArray());
    }

    @Test
    void ToList() {
        int[] source = new int[]{1, 2, 3, 4, 5};
        assertEquals(Linq.of(new int[]{1, 2, 3, 4, 5}), Linq.of(Linq.of(source).take(5).toList()));
        assertEquals(Linq.of(new int[]{1, 2, 3, 4, 5}), Linq.of(Linq.of(source).take(6).toList()));
        assertEquals(Linq.of(new int[]{1, 2, 3, 4, 5}), Linq.of(Linq.of(source).take(40).toList()));
        assertEquals(Linq.of(new int[]{1, 2, 3, 4}), Linq.of(Linq.of(source).take(4).toList()));
        assertEquals(1, Linq.of(Linq.of(source).take(1).toList()).single());
        assertEmpty(Linq.of(Linq.of(source).take(0).toList()));
        assertEmpty(Linq.of(Linq.of(source).take(-10).toList()));
    }

    @Test
    void ToListNotList() {
        IEnumerable<Integer> source = GuaranteeNotIList(Linq.of(new int[]{1, 2, 3, 4, 5}));
        assertEquals(Linq.of(new int[]{1, 2, 3, 4, 5}), Linq.of(source.take(5).toList()));
        assertEquals(Linq.of(new int[]{1, 2, 3, 4, 5}), Linq.of(source.take(6).toList()));
        assertEquals(Linq.of(new int[]{1, 2, 3, 4, 5}), Linq.of(source.take(40).toList()));
        assertEquals(Linq.of(new int[]{1, 2, 3, 4}), Linq.of(source.take(4).toList()));
        assertEquals(1, Linq.of(source.take(1).toList()).single());
        assertEmpty(Linq.of(source.take(0).toList()));
        assertEmpty(Linq.of(source.take(-10).toList()));
    }

    @Test
    void TakeCanOnlyBeOneList() {
        int[] source = new int[]{2, 4, 6, 8, 10};
        assertEquals(Linq.of(new int[]{2}), Linq.of(source).take(1));
        assertEquals(Linq.of(new int[]{4}), Linq.of(source).skip(1).take(1));
        assertEquals(Linq.of(new int[]{6}), Linq.of(source).take(3).skip(2));
        assertEquals(Linq.of(new int[]{2}), Linq.of(source).take(3).take(1));
    }

    @Test
    void TakeCanOnlyBeOneNotList() {
        IEnumerable<Integer> source = GuaranteeNotIList(Linq.of(new int[]{2, 4, 6, 8, 10}));
        assertEquals(Linq.of(new int[]{2}), Linq.of(source).take(1));
        assertEquals(Linq.of(new int[]{4}), Linq.of(source).skip(1).take(1));
        assertEquals(Linq.of(new int[]{6}), Linq.of(source).take(3).skip(2));
        assertEquals(Linq.of(new int[]{2}), Linq.of(source).take(3).take(1));
    }

    @Test
    void RepeatEnumerating() {
        int[] source = new int[]{1, 2, 3, 4, 5};
        IEnumerable<Integer> taken = Linq.of(source).take(3);
        assertEquals(taken, taken);
    }

    @Test
    void RepeatEnumeratingNotList() {
        IEnumerable<Integer> source = GuaranteeNotIList(Linq.of(new int[]{1, 2, 3, 4, 5}));
        IEnumerable<Integer> taken = source.take(3);
        assertEquals(taken, taken);
    }

    @ParameterizedTest
    @MethodSource("LazySkipAllTakenForLargeNumbers_TestData")
    void LazySkipAllTakenForLargeNumbers(int largeNumber) {
        assertEmpty(new FastInfiniteEnumerator<Integer>().take(largeNumber).skip(largeNumber));
        assertEmpty(new FastInfiniteEnumerator<Integer>().take(largeNumber).skip(largeNumber).skip(42));
        assertEmpty(new FastInfiniteEnumerator<Integer>().take(largeNumber).skip(largeNumber / 2).skip(largeNumber / 2 + 1));
    }

    @Test
    void LazyOverflowRegression() {
        IEnumerable<Integer> range = NumberRangeGuaranteedNotCollectionType(1, 100);
        IEnumerable<Integer> skipped = range.skip(42); // Min index is 42.
        IEnumerable<Integer> taken = skipped.take(Integer.MAX_VALUE); // May try to calculate max index as 42 + Integer.MAX_VALUE, leading to integer overflow.
        assertEquals(Linq.range(43, 100 - 42), taken);
        assertEquals(100 - 42, taken.count());
        assertEquals(Linq.range(43, 100 - 42), taken.toArray());
        assertEquals(Linq.range(43, 100 - 42), Linq.of(taken.toList()));
    }

    @ParameterizedTest
    @MethodSource("CountOfLazySkipTakeChain_TestData")
    void CountOfLazySkipTakeChain(int skip, int take, int expected) {
        IEnumerable<Integer> partition = NumberRangeGuaranteedNotCollectionType(1, 100).skip(skip).take(take);
        assertEquals(expected, partition.count());
        assertEquals(expected, partition.select(i -> i).count());
        assertEquals(expected, partition.select(i -> i).toArray()._getCount());
    }

    @ParameterizedTest
    @MethodSource("FirstAndLastOfLazySkipTakeChain_TestData")
    void FirstAndLastOfLazySkipTakeChain(IEnumerable<Integer> source, int skip, int take, Integer first, Integer last) {
        IEnumerable<Integer> partition = ForceNotCollection(source).skip(skip).take(take);

        assertEquals(first, partition.firstOrDefault());
        assertEquals(first, partition.elementAtOrDefault(0));
        assertEquals(last, partition.lastOrDefault());
        assertEquals(last, partition.elementAtOrDefault(partition.count() - 1));
    }

    @ParameterizedTest
    @MethodSource("ElementAtOfLazySkipTakeChain_TestData")
    void ElementAtOfLazySkipTakeChain(IEnumerable<Integer> source, int skip, int take, int[] indices, Integer[] expectedValues) {
        IEnumerable<Integer> partition = ForceNotCollection(source).skip(skip).take(take);

        assertEquals(indices.length, expectedValues.length);
        for (int i = 0; i < indices.length; i++) {
            assertEquals(expectedValues[i], partition.elementAtOrDefault(indices[i]));
        }
    }

    @ParameterizedTest
    @MethodSource("DisposeSource_TestData")
    void DisposeSource(int sourceCount, int count) {
        ref<Integer> state = ref.init(0);

        IEnumerable<Integer> source = new DelegateIterator<>(
                () -> ++state.value <= sourceCount,
                () -> 0,
                () -> state.value = -1);

        IEnumerator<Integer> iterator = source.take(count).enumerator();
        int iteratorCount = Math.min(sourceCount, Math.max(0, count));
        assertAll(Linq.range(0, iteratorCount), x -> assertTrue(iterator.moveNext()));

        assertFalse(iterator.moveNext());

        // Unlike Skip, Take can tell straightaway that it can return a sequence with no elements if count <= 0.
        // The enumerable it returns is a specialized empty iterator that has no connections to the source. Hence,
        // after MoveNext returns false under those circumstances, it won't invoke Dispose on our enumerator.
        int expected = count <= 0 ? 0 : -1;
        assertEquals(expected, state.value);
    }

    @Test
    void testTake() {
        List<Department> enumerableDeptsResult = Linq.of(depts).take(2).toList();
        assertEquals(2, enumerableDeptsResult.size());
        assertEquals(depts[0], enumerableDeptsResult.get(0));
        assertEquals(depts[1], enumerableDeptsResult.get(1));

        List<Department> enumerableDeptsResult5 = Linq.of(depts).take(5).toList();
        assertEquals(3, enumerableDeptsResult5.size());

        try (IEnumerator<Department> e = Linq.of(depts).take(1).enumerator()) {
            assertTrue(e.moveNext());
            assertFalse(e.moveNext());
        }
    }

    @Test
    void testIList_Take() {
        IEnumerable<Integer> source = Linq.of(new LinkedList<>(Arrays.asList(110, 98, 18, -200, 48, 50, -2, 0))).take(7);
        assertEquals(source.runOnce(), source.runOnce());
        assertEquals(Linq.of(55, 49, 9, -100, 24, 25, -1), source.select(x -> x / 2));
        assertEquals(Linq.of(110, 98, 18, -200, 48, 50, -2), source.toArray());
        assertEquals(Linq.of(110, 98, 18, -200, 48, 50, -2), Linq.of(source.toArray(Integer.class)));
        assertEquals(Arrays.asList(110, 98, 18, -200, 48, 50, -2), source.toList());
        assertEquals(7, source.count());
        assertEquals(Linq.of(98, 18, -200, 48, 50, -2), source.skip(1));
        assertEquals(Linq.of(110), source.take(1));
        assertEquals(110, source.elementAt(0));
        assertThrows(ArgumentOutOfRangeException.class, () -> source.elementAt(-1));
        assertEquals(110, source.first());
        assertEquals(-2, source.last());

        IEnumerable<Integer> emptySource = Linq.of(new LinkedList<>(Arrays.asList(1, 2, 3, 4, 5))).skip(5).take(3);
        assertSame(ArrayUtils.empty(), emptySource.toArray().getArray());
        assertEquals(Linq.empty(), Linq.of(emptySource.toArray(Integer.class)));
        assertEquals(Collections.emptyList(), emptySource.toList());
        assertThrows(InvalidOperationException.class, () -> emptySource.first());
        assertThrows(InvalidOperationException.class, () -> emptySource.last());

        IEnumerable<Integer> emptySource2 = Linq.of(new LinkedList<>(Collections.<Integer>emptyList())).take(1);
        assertSame(ArrayUtils.empty(), emptySource2.toArray().getArray());
        assertEquals(Linq.empty(), Linq.of(emptySource2.toArray(Integer.class)));
        assertEquals(Collections.emptyList(), emptySource2.toList());
        assertEquals(0, emptySource2.count());
        assertThrows(InvalidOperationException.class, () -> emptySource2.first());
        assertThrows(InvalidOperationException.class, () -> emptySource2.last());
        assertIsType(Linq.empty().getClass(), emptySource2.skip(2));
        try (IEnumerator<Integer> e = emptySource2.enumerator()) {
            assertFalse(e.moveNext());
            assertFalse(e.moveNext());
        }
    }

    @Test
    void testIList_TakeLast() {
        IEnumerable<Integer> source = Linq.of(new LinkedList<>(Arrays.asList(110, 98, 18, -200, 48, 50, -2, 0))).takeLast(7);
        assertEquals(source.runOnce(), source.runOnce());
        assertEquals(Linq.of(49, 9, -100, 24, 25, -1, 0), source.select(x -> x / 2));
        assertEquals(Linq.of(98, 18, -200, 48, 50, -2, 0), source.toArray());
        assertEquals(Linq.of(98, 18, -200, 48, 50, -2, 0), Linq.of(source.toArray(Integer.class)));
        assertEquals(Arrays.asList(98, 18, -200, 48, 50, -2, 0), source.toList());
        assertEquals(7, source.count());
        assertEquals(Linq.of(18, -200, 48, 50, -2, 0), source.skip(1));
        assertEquals(Linq.of(98), source.take(1));
        assertEquals(98, source.elementAt(0));
        assertThrows(ArgumentOutOfRangeException.class, () -> source.elementAt(-1));
        assertEquals(98, source.first());
        assertEquals(0, source.last());

        IEnumerable<Integer> emptySource = Linq.of(new LinkedList<>(Arrays.asList(1, 2, 3, 4, 5))).skip(5).takeLast(3);
        assertSame(ArrayUtils.empty(), emptySource.toArray().getArray());
        assertEquals(Linq.empty(), Linq.of(emptySource.toArray(Integer.class)));
        assertEquals(Collections.emptyList(), emptySource.toList());
        assertThrows(InvalidOperationException.class, () -> emptySource.first());
        assertThrows(InvalidOperationException.class, () -> emptySource.last());

        IEnumerable<Integer> emptySource2 = Linq.of(new LinkedList<>(Collections.<Integer>emptyList())).takeLast(1);
        assertSame(ArrayUtils.empty(), emptySource2.toArray().getArray());
        assertEquals(Linq.empty(), Linq.of(emptySource2.toArray(Integer.class)));
        assertEquals(Collections.emptyList(), emptySource2.toList());
        assertEquals(0, emptySource2.count());
        assertThrows(InvalidOperationException.class, () -> emptySource2.first());
        assertThrows(InvalidOperationException.class, () -> emptySource2.last());
        assertIsType(Linq.empty().getClass(), emptySource2.skip(2));
        try (IEnumerator<Integer> e = emptySource2.enumerator()) {
            assertFalse(e.moveNext());
            assertFalse(e.moveNext());
        }
    }
}
