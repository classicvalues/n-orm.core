package com.googlecode.n_orm.query;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.NavigableSet;

import com.googlecode.n_orm.Callback;
import com.googlecode.n_orm.CloseableIterator;
import com.googlecode.n_orm.DatabaseNotReachedException;
import com.googlecode.n_orm.PersistingElement;
import com.googlecode.n_orm.Process;
import com.googlecode.n_orm.StorageManagement;
import com.googlecode.n_orm.StoreSelector;
import com.googlecode.n_orm.WaitingCallBack;
import com.googlecode.n_orm.storeapi.ActionnableStore;
import com.googlecode.n_orm.storeapi.Store;

public class SearchableClassConstraintBuilder<T extends PersistingElement>
		extends ClassConstraintBuilder<T> {

	private Integer limit = null;
	private String [] toBeActivated = null; //null: no activation, non null: autoactivation


	public SearchableClassConstraintBuilder(Class<T> clazz) {
		super(clazz);
	}

	Integer getLimit() {
		return limit;
	}

	void setLimit(int limit) {
		if (this.limit != null) {
			throw new IllegalArgumentException("A limit is already set to " + this.limit);
		}
		this.limit = limit;
	}

	/**
	 * Returns whether a limit was set for this query.
	 */
	public boolean hasNoLimit() {
		return this.limit == null || this.limit < 1;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	Class<T> getClazz() {
		return (Class<T>) super.getClazz();
	}

	@Override
	public KeyConstraintBuilder<T> createKeyBuilder(
			Field f) {
		return new SearchableKeyConstraintBuilder<T>(this, f);
	}

	public LimitConstraintBuilder<T> withAtMost(int limit) {
		return new LimitConstraintBuilder<T>(this, limit);
	}

	public SearchableClassConstraintBuilder<T> andActivate(String... families) {
		if (this.toBeActivated == null)
			this.toBeActivated = families;
		else {
			String [] tba = new String [families.length];
			System.arraycopy(this.toBeActivated, 0, tba, 0, this.toBeActivated.length);
			System.arraycopy(families, 0, tba, this.toBeActivated.length, families.length);
			this.toBeActivated = tba;
		}
		return this;
	}
	
	/**
	 * Find the element with the given id.
	 * Any limit set by {@link #withAtMost(int)} will be ignored.
	 * Specified column families are activated if not already (i.e. this element is already known by this thread) (see {@link PersistingElement#activateIfNotAlready(String...)}) ; you should rather perform an {@link PersistingElement#activate(String...)} without specifying any column family with {@link #andActivate(String...)} to force activation.
	 * @param id non printable character string
	 * @return element with given id and class ; null if not found
	 * @throws DatabaseNotReachedException
	 */
	public T withId(String id) throws DatabaseNotReachedException {
		T ret = StorageManagement.getElement(getClazz(), id);
		if (toBeActivated != null)
			ret.activateIfNotAlready(toBeActivated);
		return ret;
	}
	
	/**
	 * Runs the query to find at most N matching elements. The maximum limit N must be set before using {@link #withAtMost(int)}.
	 * Elements are not activated, but their keys are all loaded into memory.
	 * @return A (possibly empty) set of elements matching the query limited to the maximum limit.
	 * @throws DatabaseNotReachedException
	 */
	public NavigableSet<T> go() throws DatabaseNotReachedException {
		if (hasNoLimit())
			throw new IllegalStateException("No limit set ; please use withAtMost expression.");
		return StorageManagement.findElementsToSet(this.getClazz(), this.getConstraint(), this.limit, this.toBeActivated);
	}
	
	/**
	 * Runs the query to find at most N matching elements. The maximum limit N must be set before using {@link #withAtMost(int)}.
	 * Elements are not activated. Instead of this function, you should consider using {@link #forEach(Process)}.
	 * @return A (possibly empty) set of elements matching the query limited to the maximum limit.
	 * @throws DatabaseNotReachedException
	 */
	public CloseableIterator<T> iterate() throws DatabaseNotReachedException {
		if (hasNoLimit())
			throw new IllegalStateException("No limit set ; please use withAtMost expression.");
		return StorageManagement.findElement(this.getClazz(), this.getConstraint(), this.limit, this.toBeActivated);
	}

	
	/**
	 * Runs the query to find an element matching the query. Any limit set by {@link #withAtMost(int)} will be ignored.
	 * The element is not activated.
	 * @return A (possibly null) element matching the query.
	 * @throws DatabaseNotReachedException
	 */
	public T any()  throws DatabaseNotReachedException {
		CloseableIterator<T> found = StorageManagement.findElement(this.getClazz(), this.getConstraint(), 1, this.toBeActivated);
		try {
			if (found.hasNext())
				return found.next();
			else
				return null;
		} finally {
			found.close();
		}
	}
	
	/**
	 * Runs the query to find the number of matching elements.
	 * Any limit set by {@link #withAtMost(int)} will be ignored.
	 */
	public long count() throws DatabaseNotReachedException {
		return StorageManagement.countElements(this.getClazz(), this.getConstraint());
	}

	public SearchableKeyConstraintBuilder<T> withKey(String key) {
		return (SearchableKeyConstraintBuilder<T>) this.withKeyInt(key);
	}

	public SearchableKeyConstraintBuilder<T> andWithKey(String key) {
		return this.withKey(key);
	}
	
	/**
	 * Performs an action for each element corresponding to the query. The maximum limit N must be set before using {@link #withAtMost(int)}.
	 * @param action the action to be performed over each element of the query.
	 * @throws DatabaseNotReachedException
	 */
	public void forEach(Process<T> action) throws DatabaseNotReachedException {
		Store s = StoreSelector.getInstance().getStoreFor(this.getClazz());
		if (hasNoLimit())
			throw new IllegalStateException("No limit set while store " + s + " for " + this.getClazz().getName() + " is not implementing " + ActionnableStore.class.getName() + " ; please use withAtMost expression.");
		StorageManagement.processElements(this.getClazz(), this.getConstraint(), action, this.limit, this.toBeActivated);
	}
	
	/**
	 * Performs <i>asynchronously</i> an action for each element corresponding to the query.
	 * In case store for class is <i>not</i> implementing {@link ActionnableStore}, this action is equivalent to an asynchronous call to {@link #forEach(Process)}, and the maximum limit N must be set before using {@link #withAtMost(int)}.
	 * Otherwise, the action is performed directly using the data store server process, and the limit is useless.
	 * In order to wait for process completion, you can use {@link WaitingCallBack}.
	 * @param action class of the action to be performed over each element of the query ; must have a default constructor
	 * @param callBack a callback to be invoked as soon as the process completes ; can be null
	 * @throws DatabaseNotReachedException
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 */
	public void remoteForEach(Process<T> action, Callback callBack) throws DatabaseNotReachedException, InstantiationException, IllegalAccessException {
		Store s = StoreSelector.getInstance().getStoreFor(this.getClazz());
		if ((!(s instanceof ActionnableStore)) && hasNoLimit())
			throw new IllegalStateException("No limit set while store " + s + " for " + this.getClazz().getName() + " is not implementing " + ActionnableStore.class.getName() + " ; please use withAtMost expression.");
		int limit;
		if (this.limit == null)
			limit = -1;
		else
			limit = this.limit;
		StorageManagement.processElementsRemotely(this.getClazz(), this.getConstraint(), action, callBack, limit, this.toBeActivated);
	}
	
	/**
	 * Serialize the elements in a OutputStream
	 * @throws IOException 
	 */
	public void serialize(OutputStream out) throws IOException {
		ObjectOutputStream oos= new ObjectOutputStream(out);

		NavigableSet<T> set = this.go();

		oos.writeObject(set);
		
		oos.flush();
		oos.close();
		out.flush();
	}
}
