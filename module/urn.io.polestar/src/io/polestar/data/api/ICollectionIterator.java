package io.polestar.data.api;

public interface ICollectionIterator
{
	/** more to next value if available
	 * @return true if there is a next item to move to
	 */
	boolean next();
	/** @return time of current item */
	long getTime();
	/** @return value of current item */
	Object getValue();
	
	/** set iterator back to initial state */
	void reset();
}