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
package io.sapl.interpreter.combinators;

import static io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES;
import static io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm.ONLY_ONE_APPLICABLE;
import static io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm.PERMIT_OVERRIDES;
import static io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY;

import io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DocumentsCombinatorFactory {

	public static DocumentsCombinator getCombinator(PolicyDocumentCombiningAlgorithm algorithm) {
		if (algorithm == PERMIT_UNLESS_DENY)
			return new PermitUnlessDenyCombinator();
		if (algorithm == PERMIT_OVERRIDES)
			return new PermitOverridesCombinator();
		if (algorithm == DENY_OVERRIDES)
			return new DenyOverridesCombinator();
		if (algorithm == ONLY_ONE_APPLICABLE)
			return new OnlyOneApplicableCombinator();

		return new DenyUnlessPermitCombinator();
	}
}
