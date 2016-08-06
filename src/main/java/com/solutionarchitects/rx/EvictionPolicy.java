package com.solutionarchitects.rx;

/**
 * Created by e211303 on 3/24/2016.
 */

/** Interface to manage eviction checking. */
interface EvictionPolicy {
    /**
     * Subscribe-time checking for stale entries.
     * @param value the value to test
     * @param now the current time
     * @return true if the value may be evicted
     */
    boolean test(Object value, long now);
    /**
     * Evict values from the list.
     * @param list the node list
     */
    void evict(NodeList<Object> list);
    /**
     * Evict values from the list except the very last which is considered
     * a terminal event
     * @param list the node list
     */
    void evictFinal(NodeList<Object> list);
}