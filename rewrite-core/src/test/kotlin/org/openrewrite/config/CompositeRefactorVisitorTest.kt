/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.Eval
import org.openrewrite.Tree.randomId
import org.openrewrite.text.PlainText
import java.util.*

class CompositeRefactorVisitorTest {
    @Test
    fun delegateToVisitorsInOrder() {
        val yaml = """
            type: specs.openrewrite.org/v1beta/visitor
            name: org.openrewrite.text.ChangeTextTwice
            visitors:
              - org.openrewrite.text.ChangeText:
                  toText: Hello Jon
              - org.openrewrite.text.ChangeText:
                  toText: Hello Jonathan!
        """.trimIndent()

        val loader = YamlResourceLoader(yaml.byteInputStream(), null, Properties())

        val visitors = loader.loadVisitors()

        val fixed = Eval.builder().visit(*visitors.toTypedArray()).build()
                .results(PlainText(randomId(), "Hi Jon", emptyList()))

        assertThat(fixed!!.printTrimmed()).isEqualTo("Hello Jonathan!")
    }
}
