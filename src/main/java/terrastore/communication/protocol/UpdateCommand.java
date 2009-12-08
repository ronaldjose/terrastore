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
package terrastore.communication.protocol;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import terrastore.cluster.impl.ClusterLocator;
import terrastore.store.Bucket;
import terrastore.store.Store;
import terrastore.store.StoreOperationException;
import terrastore.store.features.Update;
import terrastore.store.Value;
import terrastore.store.operators.Function;

/**
 * @author Sergio Bossa
 */
public class UpdateCommand extends AbstractCommand {

    private final String bucketName;
    private final String key;
    private final Update update;
    private final Function function;

    public UpdateCommand(String bucketName, String key, Update update, Function function) {
        this.bucketName = bucketName;
        this.key = key;
        this.update = update;
        this.function = function;
    }

    public Map<String, Value> executeOn(Store store) throws StoreOperationException {
        Bucket bucket = store.get(bucketName);
        if (bucket != null) {
            // WARN: use singleton locator due to Terracotta not supporting injection in clustered objects:
            ExecutorService updateExecutor = ClusterLocator.getCluster().getWorkerExecutor();
            //
            bucket.update(key, update, function, updateExecutor);
        }
        return new HashMap<String, Value>(0);
    }
}
