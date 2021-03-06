/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.cypher.internal.v3_6.parser.matchers

import org.parboiled.MatcherContext
import org.parboiled.matchers.CustomMatcher

abstract class ScalaCharMatcher(label: String) extends CustomMatcher(label) {

  protected def matchChar(c: Char): Boolean

  def `match`[V](context: MatcherContext[V]): Boolean =
    if (matchChar(context.getCurrentChar)) {
      context.advanceIndex(1)
      context.createNode()
      true
    } else {
      false
    }

  def isSingleCharMatcher: Boolean = true

  def canMatchEmpty: Boolean = false

  def isStarterChar(c: Char): Boolean = matchChar(c)

  def getStarterChar: Char = 'a'
}
