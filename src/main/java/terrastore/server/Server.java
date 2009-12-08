/**
 * Copyright 2009 Sergio Bossa (sergio.bossa@gmail.com)
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package terrastore.server;

import terrastore.service.QueryService;
import terrastore.service.UpdateService;
import terrastore.store.Value;

/**
 * The Server handles client requests relying on the {@link terrastore.service.UpdateService}
 * and {@link terrastore.service.QueryService} to actually execute them.
 *
 * @author Sergio Bossa
 */
public interface Server {

    /**
     * Add the given bucket.
     *
     * @param bucket The name of the bucket to add.
     * @throws ServerOperationException If an error occurs.
     */
    public void addBucket(String bucket) throws ServerOperationException;

    /**
     * Remove the given bucket.
     *
     * @param bucket The name of the bucket to remove.
     * @throws ServerOperationException If an error occurs.
     */
    public void removeBucket(String bucket) throws ServerOperationException;

    /**
     * Put a value in the given bucket under the given key.
     *
     * @param bucket The name of the bucket where to put the value.
     * @param key The key of the value to put.
     * @param value The value to put.
     * @throws ServerOperationException If an error occurs.
     */
    public void putValue(String bucket, String key, Value value) throws ServerOperationException;

    /**
     * Remove a value from the given bucket under the given key.
     *
     * @param bucket The name of the bucket where to remove the value from.
     * @param key The key of the value to remove.
     * @throws ServerOperationException If an error occurs.
     */
    public void removeValue(String bucket, String key) throws ServerOperationException;

    /**
     * Execute an update on a value from the given bucket under the given key.
     * 
     * @param bucket The name of the bucket holding the value to update.
     * @param key The key of the value to update.
     * @param function The name of the server-side function performing the actual update.
     * @param timeoutInMillis The timeout for the update operation (update operations lasting more than the given timeout will be aborted).
     * @param parameters The update operation parameters.
     * @throws ServerOperationException If an error occurs.
     */
    public void updateValue(String bucket, String key, String function, Long timeoutInMillis, Parameters parameters) throws ServerOperationException;

    /**
     * Get the value from the given bucket under the given key.
     *
     * @param bucket The name of the bucket containing the value to get.
     * @param key The key of the value to get.
     * @return The value.
     * @throws ServerOperationException If an error occurs.
     */
    public Value getValue(String bucket, String key) throws ServerOperationException;

    /**
     * Get all key/value entries into the given bucket.
     *
     * @param bucket The name of the bucket containing the values to get.
     * @return A map containing all key/value entries.
     * @throws ServerOperationException If an error occurs.
     */
    public Values getAllValues(String bucket) throws ServerOperationException;

    /**
     * Execute a range query returning all key/value entries whose key falls into the given range.<br/>
     * Results are not computed over a live view of the data, so the timeToLive parameter determines,
     * in milliseconds, how fresh the data has to be.
     *
     * @param bucket The bucket to query.
     * @param startKey First key in range.
     * @param endKey Last key in range (comprised).
     * @param comparator Name of the comparator to use for testing if a key is in range.
     * @param timeToLive Number of milliseconds determining how fresh the retrieved data has to be; if set to 0, the query will be immediately computed
     * on current data.
     * @return A map containing key/value pairs
     * @throws ServerOperationException If an error occurs.
     */
    public Values doRangeQuery(String bucket, String startKey, String endKey, String comparator, String predicate, long timeToLive) throws ServerOperationException;

    /**
     * Get the {@link terrastore.service.UpdateService} which will actually execute all update operations.
     *
     * @return The {@link terrastore.service.UpdateService} instance.
     */
    public UpdateService getUpdateService();

    /**
     * Get the {@link terrastore.service.QueryService} which will actually execute all query operations.
     *
     * @return The {@link terrastore.service.QueryService} instance.
     */
    public QueryService getQueryService();
}
