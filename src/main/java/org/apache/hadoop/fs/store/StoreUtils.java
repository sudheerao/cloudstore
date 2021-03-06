/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.fs.store;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class StoreUtils {

  /** life without Guava. */
  public static void checkArgument(boolean condition, String text) {
    if (!condition) {
      throw new IllegalArgumentException(text);
    }
  }

  public static void checkState(boolean condition, String text) {
    if (!condition) {
      throw new IllegalStateException(text);
    }
  }


  /**
   * Take an exception from a Future and convert to an IOE.
   * @param ex exception
   * @return the extracted or wrapped exception
   */
  public static IOException uprate(ExecutionException ex) {
    Throwable cause = ex.getCause();
    if (cause instanceof RuntimeException) {
      throw (RuntimeException) cause;
    }
    if (cause instanceof IOException) {
      return (IOException) cause;
    }
    return new IOException(cause.toString(), cause);
  }

  /**
   * Await a future completing; uprate failures.
   * @param future future to exec
   * @param <T> return type
   * @return the result
   * @throws IOException failure
   * @throws InterruptedException work interrupted
   */
  public static <T> T await(Future<T> future)
      throws IOException, InterruptedException {
    try {
      return future.get();
    } catch (ExecutionException e) {
      throw uprate(e);
    }
  }

  /**
   * split a key=value pair. Why not return a Pair class? Commons-lang
   * versions: don't want to commit.
   * @param param param to split
   * @return a param split key = val,
   */
  public static Map.Entry<String, String> split(String param, String defVal) {
    int split = param.indexOf('=');
    int len = param.length();
    if (split == 0 || split + 1 == len) {
      throw new StoreExitException(StoreEntryPoint.EXIT_USAGE,
          "Unable to parse argument " + param);
    }
    String key = split > 0 ? param.substring(0, split) : param;
    String value = split > 0 ? param.substring(split + 1, len) : defVal;
    return new StringPair(key, value);
  }

  /**
   * Concatenate two arrays into a new one.
   * If either array is empty, the other array is returned; no new
   * array is created.
   * see https://stackoverflow.com/questions/80476/how-can-i-concatenate-two-arrays-in-java
   * @param left left side
   * @param right right side
   * @param <T> type of arrays
   * @return the expanded array.
   */
  public static <T> T[] cat(T[] left, T[] right) {
    int aLen = left.length;
    int bLen = right.length;
    if (aLen == 0) {
      return right;
    }
    if (bLen == 0) {
      return left;
    }

    T[] dest = (T[]) Array.newInstance(left.getClass().getComponentType(),
        aLen + bLen);
    System.arraycopy(left, 0, dest, 0, aLen);
    System.arraycopy(right, 0, dest, aLen, bLen);

    return dest;
  }

  public static class StringPair implements Map.Entry<String, String>{
    private String key, value;

    private StringPair(final String left, final String right) {
      this.key = left;
      this.value = right;
    }

    public String getKey() {
      return key;
    }

    public String getValue() {
      return value;
    }

    @Override
    public String setValue(final String value) {
      this.value = value;
      return value;
    }
  }

}
