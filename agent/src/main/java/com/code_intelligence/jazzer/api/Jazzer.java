// Copyright 2021 Code Intelligence GmbH
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.code_intelligence.jazzer.api;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;

/**
 * Helper class with static methods that interact with Jazzer at runtime.
 */
final public class Jazzer {
  private static Class<?> jazzerInternal = null;

  private static MethodHandle traceStrcmp = null;
  private static MethodHandle traceStrstr = null;

  static {
    try {
      jazzerInternal = Class.forName("com.code_intelligence.jazzer.runtime.JazzerInternal");
      Class<?> traceDataFlowNativeCallbacks =
          Class.forName("com.code_intelligence.jazzer.runtime.TraceDataFlowNativeCallbacks");

      // Use method handles for hints as the calls are potentially performance critical.
      MethodType traceStrcmpType =
          MethodType.methodType(void.class, String.class, String.class, int.class, int.class);
      traceStrcmp = MethodHandles.publicLookup().findStatic(
          traceDataFlowNativeCallbacks, "traceStrcmp", traceStrcmpType);
      MethodType traceStrstrType =
          MethodType.methodType(void.class, String.class, String.class, int.class);
      traceStrstr = MethodHandles.publicLookup().findStatic(
          traceDataFlowNativeCallbacks, "traceStrstr", traceStrstrType);
    } catch (ClassNotFoundException ignore) {
      // Not running in the context of the agent. This is fine as long as no methods are called on
      // this class.
    } catch (NoSuchMethodException | IllegalAccessException e) {
      // This should never happen as the Jazzer API is loaded from the agent and thus should always
      // match the version of the runtime classes.
      System.err.println("ERROR: Incompatible version of the Jazzer API detected, please update.");
      e.printStackTrace();
      System.exit(1);
    }
  }

  /**
   * Instructs the fuzzer to guide its mutations towards making {@code current} equal to {@code
   * target}.
   *
   * If the relation between the raw fuzzer input and the value of {@code current} is relatively
   * complex, running the fuzzer with the argument {@code -use_value_profile=1} may be necessary to
   * achieve equality.
   *
   * @param current a non-constant string observed during fuzz target execution
   * @param target a string that {@code current} should become equal to, but currently isn't
   * @param id a (probabilistically) unique identifier for this particular compare hint
   */
  public static void guideTowardsEquality(String current, String target, int id) {
    try {
      traceStrcmp.invokeExact(current, target, 1, id);
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  /**
   * Instructs the fuzzer to guide its mutations towards making {@code haystack} contain {@code
   * needle} as a substring.
   *
   * If the relation between the raw fuzzer input and the value of {@code haystack} is relatively
   * complex, running the fuzzer with the argument {@code -use_value_profile=1} may be necessary to
   * satisfy the substring check.
   *
   * @param haystack a non-constant string observed during fuzz target execution
   * @param needle a string that should be contained in {@code haystack} as a substring, but
   *     currently isn't
   * @param id a (probabilistically) unique identifier for this particular compare hint
   */
  public static void guideTowardsContainment(String haystack, String needle, int id) {
    try {
      traceStrstr.invokeExact(haystack, needle, id);
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  /**
   * Make Jazzer report the provided {@link Throwable} as a finding.
   *
   * <b>Note:</b> This method must only be called from a method hook. In a
   * fuzz target, simply throw an exception to trigger a finding.
   * @param finding the finding that Jazzer should report
   */
  public static void reportFindingFromHook(Throwable finding) {
    try {
      jazzerInternal.getMethod("reportFindingFromHook", Throwable.class).invoke(null, finding);
    } catch (NullPointerException | IllegalAccessException | NoSuchMethodException e) {
      // We can only reach this point if the runtime is not in the classpath, but it must be if
      // hooks work and this function should only be called from them.
      System.err.println("ERROR: Jazzer.reportFindingFromHook must be called from a method hook");
      System.exit(1);
    } catch (InvocationTargetException e) {
      // reportFindingFromHook throws a HardToCatchThrowable, which will bubble up wrapped in an
      // InvocationTargetException that should not be stopped here.
      if (e.getCause().getClass().getName().endsWith(".HardToCatchError")) {
        throw(Error) e.getCause();
      } else {
        e.printStackTrace();
      }
    }
  }
}
