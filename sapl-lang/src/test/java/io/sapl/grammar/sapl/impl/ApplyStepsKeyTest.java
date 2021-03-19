/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.sapl.impl;

import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionErrors;
import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionEvaluatesTo;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.interpreter.EvaluationContext;

class ApplyStepsKeyTest {

	private final static EvaluationContext CTX = MockUtil.constructTestEnvironmentPdpScopedEvaluationContext();

	@Test
	void keyStepPropagatesErrors() {
		expressionErrors(CTX, "(10/0).key");
	}

	@Test
	void keyStepToNonObjectUndefined() {
		expressionEvaluatesTo(CTX, "true.key", Val.UNDEFINED);
	}

	@Test
	void keyStepToEmptyObject() {
		expressionEvaluatesTo(CTX, "{}.key", Val.UNDEFINED);
	}

	@Test
	void keyStepToObject() {
		expressionEvaluatesTo(CTX, "{\"key\" : true}.key", "true");
	}

	@Test
	void keyStepToArray() {
		var expression = "[{\"key\" : true},{\"key\": 123}].key";
		var expected = "[true,123]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	// FIXME: {"key",123} should be rejected at parse time
	@Test
	void keyStepToArrayNoMatch() {
		var expression = "[{\"key\" : true},{\"key\": 123}].x";
		var expected = "[]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	void filterNonObjectOrArray() {
		var expression = "\"Gudrun\" |- { @.key : mock.nil }";
		var expected = "\"Gudrun\"";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	void filterObject() {
		var expression = "{\"key\" : true, \"other\" : false} |- { @.key : mock.nil}";
		var expected = "{\"key\" : null, \"other\" : false}";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	void filterObjectDescend() {
		var expression = "{\"key\" : { \"key2\" : true}, \"other\" : false} |- { @.key.key2 : mock.nil}";
		var expected = "{\"key\" : {\"key2\" : null }, \"other\" : false}";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	void filterArray() {
		var expression = "[ {\"key\" : true, \"other\" : false} , false ] |- { @.key : mock.nil}";
		var expected = "[ {\"key\" : null, \"other\" : false} , false ]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	void filterEmptyrray() {
		var expression = "[] |- { @.key : mock.nil}";
		var expected = "[]";
		expressionEvaluatesTo(CTX, expression, expected);
	}

}
