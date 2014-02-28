package oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine;

import commonj.sdo.DataObject;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;


public class GroovyDataObjectList implements List<GroovyDataObject> {
    List internalList;

    public GroovyDataObjectList(List l) {
        super();
        internalList = l;
    }

    public int size() {
        return internalList.size();
    }

    public boolean isEmpty() {
        return internalList.isEmpty();
    }

    public boolean contains(Object o) {
        return internalList.contains(o);
    }

    public Iterator iterator() {
        return new DataObjectIterator();
    }

    public Object[] toArray() {
        return internalList.toArray();
    }

    public Object[] toArray(Object[] a) {
        return internalList.toArray(a);
    }

    public boolean add(GroovyDataObject e) {
        if ( e instanceof GroovyDataObject ) {
            return internalList.add(e);
        }
        else {
            throw new UnsupportedOperationException();
        }
    }

    public boolean remove(Object o) {
        return internalList.remove(o);
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
        internalList.clear();
    }

    public GroovyDataObject get(int index) {
        return new GroovyDataObject((DataObject) internalList.get(index));
    }

    public GroovyDataObject set(int index, GroovyDataObject element) {
        if ( element instanceof DataObject ) {
            return (GroovyDataObject) internalList.set(index, new GroovyDataObject((DataObject) element));
        }
        else {
            throw new UnsupportedOperationException();
        }
    }

    public void add(int index, GroovyDataObject element) {
        throw new UnsupportedOperationException();
    }

    public GroovyDataObject remove(int index) {
        throw new UnsupportedOperationException();
    }

    public int indexOf(Object o) {
        throw new UnsupportedOperationException();
    }

    public int lastIndexOf(Object o) {
        throw new UnsupportedOperationException();
    }

    public ListIterator listIterator() {
        return internalList.listIterator();
    }

    public ListIterator listIterator(int index) {
        return internalList.listIterator(index);
    }

    public List subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException();
    }

    class DataObjectIterator implements Iterator{
        Iterator internalIterator = internalList.iterator();

        public boolean hasNext() {
            return internalIterator.hasNext();
        }

        public GroovyDataObject next() {
            Object nextObj = internalIterator.next();
            if ( nextObj instanceof GroovyDataObject ) {
                return (GroovyDataObject) nextObj;
            }
            else if ( nextObj instanceof DataObject ) {
                return new GroovyDataObject((DataObject) nextObj);
            }
            else {
                throw new UnsupportedOperationException();
            }
        }

        public void remove() {
        }
    }
}
