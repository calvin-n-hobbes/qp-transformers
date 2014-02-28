package oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import oracle.jbo.Row;

public class GroovyRowList implements List<GroovyRow> {
    private List _list;

    public GroovyRowList(List internalList) {
        super();
        _list = internalList;
    }

    public int size() {
        return _list.size();
    }
    
    public boolean isEmpty() {
        return _list.isEmpty();
    }

    public boolean contains(Object o) {
        return _list.contains(o);
    }

    public Iterator iterator() {
        return new RowIterator();
    }

    public Object[] toArray() {
        return _list.toArray();
    }

    public Object[] toArray(Object[] a) {
        return _list.toArray(a);
    }

    public boolean add(GroovyRow e) {
        return _list.add(e);
    }

    public boolean remove(Object o) {
        return _list.remove(o);
    }

    public boolean containsAll(Collection c) {
        throw new UnsupportedOperationException();
    }

    public boolean addAll(Collection c) {
        throw new UnsupportedOperationException();
    }

    public boolean addAll(int index, Collection c) {
        throw new UnsupportedOperationException();
    }

    public boolean removeAll(Collection c) {
        throw new UnsupportedOperationException();
    }

    public boolean retainAll(Collection c) {
        throw new UnsupportedOperationException();
    }

    public void clear() {
        _list.clear();
    }

    public GroovyRow get(int index) {
        return new GroovyRow((Row) _list.get(index));
    }

    public GroovyRow set(int index, GroovyRow element) {
        return (GroovyRow) _list.set(index, new GroovyRow((Row) element));
    }

    public void add(int index, GroovyRow element) {
        throw new UnsupportedOperationException();
    }

    public GroovyRow remove(int index) {
        throw new UnsupportedOperationException();
    }

    public int indexOf(Object o) {
        throw new UnsupportedOperationException();
    }

    public int lastIndexOf(Object o) {
        throw new UnsupportedOperationException();
    }

    public ListIterator listIterator() {
        return _list.listIterator();
    }

    public ListIterator listIterator(int index) {
        return _list.listIterator(index);
    }

    public List subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException();
    }

    class RowIterator implements Iterator{
        Iterator internalIterator = _list.iterator();

        public boolean hasNext() {
            return internalIterator.hasNext();
        }

        public GroovyRow next() {
            Object nextObj = internalIterator.next();
            if ( nextObj instanceof GroovyRow ) {
                return (GroovyRow) nextObj;
            }
            else if ( nextObj instanceof Row ) {
                return new GroovyRow((Row) nextObj);
            }
            else {
                throw new UnsupportedOperationException();
            }
        }

        public void remove() {
        }
    }
}
