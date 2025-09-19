package dev.cel.common.values;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public final class MutableListValue<E extends CelValue> extends ListValue<E> {

  private List<E> mutableList;

  public static <E extends CelValue> MutableListValue<E> create(int size) {
    return new MutableListValue<>(size);
  }

  private MutableListValue(int size) {
    this.mutableList = new ArrayList<>(size);
  }

  @Override
  public List<E> value() {
    return List.of();
  }
  @Override
  public int size() {
    return 0;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }
  @Override
  public boolean contains(Object o) {
    return false;
  }
  @Override
  public Iterator<E> iterator() {
    return null;
  }
  @Override
  public Object[] toArray() {
    return new Object[0];
  }
  @Override
  public <T> T[] toArray(T[] a) {
    return null;
  }
  @Override
  public boolean containsAll(Collection<?> c) {
    return false;
  }
  @Override
  public E get(int index) {
    return null;
  }
  @Override
  public int indexOf(Object o) {
    return 0;
  }
  @Override
  public int lastIndexOf(Object o) {
    return 0;
  }
  @Override
  public ListIterator<E> listIterator() {
    return null;
  }
  @Override
  public ListIterator<E> listIterator(int index) {
    return null;
  }
  @Override
  public List<E> subList(int fromIndex, int toIndex) {
    return List.of();
  }
}
