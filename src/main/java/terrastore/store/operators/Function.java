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
package terrastore.store.operators;

import java.io.Serializable;
import java.util.Map;

/**
 * Interface to implement for applying functions to bucket values.
 *
 * @author Sergio Bossa
 */
public interface Function extends Serializable {

    /**
     *  Apply this function to the given value, represented as a map of name -> value pairs (associative array).
     *
     * @param value The value to apply the function to.
     * @param parameters The function parameters.
     * @return The result of the function as an associative array.
     */
    public Map<String, Object> apply(Map<String, Object> value, Map<String, Object> parameters);
}