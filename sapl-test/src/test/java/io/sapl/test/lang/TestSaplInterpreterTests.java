/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.lang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.sapl.test.coverage.api.CoverageHitRecorder;

class TestSaplInterpreterTests {

	private final String POLICY_ID = "test";

	@Test
	void testTestModeImplAreCreatedIfSystemPropertyIsNotSet() {
		var interpreter    = new TestSaplInterpreter(Mockito.mock(CoverageHitRecorder.class));
		var policyDocument = "policy \"" + POLICY_ID + "\" permit";
		var document       = interpreter.parse(policyDocument);

		assertThat(document.getPolicyElement() instanceof PolicyImplCustomCoverage).isTrue();
	}

	@Test
	void testTestModeImplAreCreatedIfSystemPropertyIsTrue() {
		System.setProperty("io.sapl.test.coverage.collect", "true");
		var interpreter    = new TestSaplInterpreter(Mockito.mock(CoverageHitRecorder.class));
		var policyDocument = "policy \"" + POLICY_ID + "\" permit";
		var document       = interpreter.parse(policyDocument);

		assertThat(document.getPolicyElement() instanceof PolicyImplCustomCoverage).isTrue();
		System.clearProperty("io.sapl.test.coverage.collect");
	}

	@Test
	void testTestModeImplAreCreatedIfSystemPropertyIsFalse() {
		System.setProperty("io.sapl.test.coverage.collect", "false");
		var interpreter    = new TestSaplInterpreter(Mockito.mock(CoverageHitRecorder.class));
		var policyDocument = "policy \"" + POLICY_ID + "\" permit";
		var document       = interpreter.parse(policyDocument);

		assertThat(document.getPolicyElement() instanceof PolicyImplCustomCoverage).isFalse();
		System.clearProperty("io.sapl.test.coverage.collect");
	}

}
