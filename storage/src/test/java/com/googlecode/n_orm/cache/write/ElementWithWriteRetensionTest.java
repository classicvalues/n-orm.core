package com.googlecode.n_orm.cache.write;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.googlecode.n_orm.Incrementing;
import com.googlecode.n_orm.Key;
import com.googlecode.n_orm.Persisting;
import com.googlecode.n_orm.StoreSelector;

public class ElementWithWriteRetensionTest {
	
	@Persisting(writeRetentionMs=100)
	public static class Element {
		@Key public String key;
		
		@Incrementing public long incr;

		public Set<String> aSet = new TreeSet<String>();
		
		@Incrementing public Map<String, Long> anIncrementingMap = new TreeMap<String, Long>();
	}
	
	public WriteRetentionStore sut;
	
	@Before
	public void setupSut() {
		sut = (WriteRetentionStore)StoreSelector.getInstance().getStoreFor(Element.class);
	}

	@After
	public void waitForPendingRequests() {
		try {
			while(WriteRetentionStore.getPendingRequests() != 0)
				Thread.sleep(10);
			Thread.sleep(10);
			while(WriteRetentionStore.getPendingRequests() != 0)
				Thread.sleep(10);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
	}
	
	@Test
	public void createDelete() {
		final String key = "anelement";
		
		Element e = new Element();
		e.key = key;
		
		e.deleteNoCache();
		assertFalse(e.existsInStore());
		
		e.store();
		assertFalse(e.existsInStore());
		this.waitForPendingRequests();
		assertTrue(e.existsInStore());
		
		e.delete();
		assertTrue(e.existsInStore());
		this.waitForPendingRequests();
		assertFalse(e.existsInStore());
	}
	
	@Test
	public void createDeleteWithFlushes() {
		final String key = "anelement";
		
		Element e = new Element();
		e.key = key;
		
		e.deleteNoCache();
		assertFalse(e.existsInStore());
		
		e.storeNoCache();
		assertTrue(e.existsInStore());

		e.deleteNoCache();
		assertFalse(e.existsInStore());
	}
	
	@Test
	public void storeSet() {
		final String key = "anelement";
		
		Element e = new Element();
		e.key = key;
		e.deleteNoCache();
		
		e.activate();
		assertTrue(e.aSet.isEmpty());

		e.aSet.add("1");
		e.aSet.add("deux");
		e.store();
		this.waitForPendingRequests();
		e.activate();
		Set<String> expected = new TreeSet<String>();
		expected.add("deux");
		expected.add("1");
		assertEquals(expected, e.aSet);
	}
	
	@Test(timeout=10000)
	public void parallelIncrements() throws InterruptedException, ExecutionException {
		final int incrNr = 1000;
		final int parallelThreads = 10;
		
		final String key = "anelement";
		
		Element e = new Element();
		e.key = key;
		e.deleteNoCache();
		
		Runnable r = new Runnable() {
			@Override
			public void run() {
				Element e = new Element();
				e.key = key;
				for(int i = 0; i < incrNr; ++i) {
					e.incr++;
					e.store();
				}
			}
		};
		Collection<Future<?>> results = new LinkedList<Future<?>>();
		ExecutorService es =  new FixedThreadPool(parallelThreads);
		for (int i = 0; i < parallelThreads; i++) {
			results.add(es.submit(r));
		}
		es.shutdown();
		
		//Waiting for results and checking exceptions
		for (Future<?> f : results) {
			f.get();
		}
		
		this.waitForPendingRequests();
		
		e.activate();
		
		assertEquals(incrNr*parallelThreads, e.incr);
	}
	
	@Test(timeout=10000)
	public final void parallelIncrementsOn2Elements() throws InterruptedException, ExecutionException {
		final int incrNr = 1000;
		final int parallelThreads = 10;
		
		final String key1 = "anelement", key2 = "anotherElement";
		Element e1 = new Element();
		e1.key = key1;
		e1.deleteNoCache();
		Element e2 = new Element();
		e2.key = key2;
		e2.deleteNoCache();
		
		Runnable r = new Runnable() {
			@Override
			public void run() {
				Element e1 = new Element();
				e1.key = key1;
				Element e2 = new Element();
				e2.key = key2;
				for(int i = 0; i < incrNr; ++i) {
					e1.incr++;
					e1.store();
					e2.incr++;
					e2.store();
				}
			}
		};
		Collection<Future<?>> results = new LinkedList<Future<?>>();
		ExecutorService es =  new FixedThreadPool(parallelThreads);
		for (int i = 0; i < parallelThreads; i++) {
			results.add(es.submit(r));
		}
		es.shutdown();
		
		//Waiting for results and checking exceptions
		for (Future<?> f : results) {
			f.get();
		}

		this.waitForPendingRequests();
		this.waitForPendingRequests();
		
		e1.activate();
		assertEquals(incrNr*parallelThreads, e1.incr);
		
		e2.activate();
		assertEquals(incrNr*parallelThreads, e2.incr);
	}
	
	@Test(timeout=10000)
	public void parallelMapIncrements() throws InterruptedException, ExecutionException {
		final int incrNr = 1000;
		final int parallelThreads = 10;
		
		final String key = "anelement", qual = "aQual";
		
		Element e = new Element();
		e.key = key;
		e.deleteNoCache();
		
		Runnable r = new Runnable() {
			@Override
			public void run() {
				Element e = new Element();
				e.key = key;
				for(int i = 0; i < incrNr; ++i) {
					Long l = e.anIncrementingMap.get(qual);
					if (l == null)
						l = 1l;
					else
						l++;
					e.anIncrementingMap.put(qual, l);
					e.store();
				}
			}
		};
		Collection<Future<?>> results = new LinkedList<Future<?>>();
		ExecutorService es =  new FixedThreadPool(parallelThreads);
		for (int i = 0; i < parallelThreads; i++) {
			results.add(es.submit(r));
		}
		es.shutdown();
		
		//Waiting for results and checking exceptions
		for (Future<?> f : results) {
			f.get();
		}
		
		this.waitForPendingRequests();
		
		e.activate("anIncrementingMap");
		
		assertEquals(incrNr*parallelThreads, ((Long)e.anIncrementingMap.get(qual)).longValue());
	}

}
