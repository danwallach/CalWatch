package org.klomp.cassowary.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class IdentityHashDoubleMapTest {

    @Test
    public void testInOut() {
        IdentityHashDoubleMap<String> map = new IdentityHashDoubleMap<String>(String.class);

        map.put("hello", 5);
        assertEquals(5, map.getDouble("hello"), 0);
        map.put("me", 7);
        assertEquals(7, map.getDouble("me"), 0);
        assertEquals(5, map.remove("hello"), 0);
        assertEquals(1, map.size());
        assertTrue(map.containsKey("me"));
        assertFalse(map.containsKey("foobar"));
    }

    @Test
    public void testKeySet() {
        IdentityHashDoubleMap<String> map = new IdentityHashDoubleMap<String>(String.class);
        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);

        Set<String> keySet = map.keySet();
        assertEquals(3, keySet.size());
        Iterator<String> i = keySet.iterator();
        String x = i.next();
        String y = i.next();
        String z = i.next();
        assertFalse(i.hasNext());
        Set<String> expect = new HashSet<String>();
        expect.add("a");
        expect.add("b");
        expect.add("c");
        System.out.println(x);
        System.out.println(y);
        System.out.println(z);
        assertTrue(expect.remove(x));
        assertTrue(expect.remove(y));
        assertTrue(expect.remove(z));
    }

    @Test
    public void testEntrySet() {
        IdentityHashDoubleMap<String> map = new IdentityHashDoubleMap<String>(String.class);
        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);

        Set<Map.Entry<String, Double>> entries = map.entrySet();
        assertEquals(3, entries.size());
        Iterator<Map.Entry<String, Double>> i = entries.iterator();
        Map.Entry<String, Double> e;
        e = i.next();
        String xk = e.getKey();
        int xv = e.getValue().intValue();
        assertEquals(xk.charAt(0) - 'a' + 1, xv);
        e = i.next();
        String yk = e.getKey();
        int yv = e.getValue().intValue();
        assertEquals(yk.charAt(0) - 'a' + 1, yv);
        e = i.next();
        String zk = e.getKey();
        int zv = e.getValue().intValue();
        assertEquals(zk.charAt(0) - 'a' + 1, zv);
        assertFalse(i.hasNext());
        Set<String> expect = new HashSet<String>();
        expect.add("a");
        expect.add("b");
        expect.add("c");

        assertTrue(expect.remove(xk));
        assertTrue(expect.remove(yk));
        assertTrue(expect.remove(zk));
    }
}
