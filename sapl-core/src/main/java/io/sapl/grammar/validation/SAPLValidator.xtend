/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

/*
 * generated by Xtext 2.13.0
 */
package io.sapl.grammar.validation

import io.sapl.grammar.sapl.And
import io.sapl.grammar.sapl.Policy
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.validation.Check
import io.sapl.grammar.sapl.AttributeFinderStep
import io.sapl.grammar.sapl.Or

/**
 * This class contains custom validation rules. 
 * 
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#validation
 */
class SAPLValidator extends AbstractSAPLValidator {

	protected static final String MSG_AND_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION = "And is not allowed in target expression."
	protected static final String MSG_OR_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION = "Or is not allowed in target expression."
	protected static final String MSG_AFS_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION = "AttributeFinderStep is not allowed in target expression."

	/**
	 * According to SAPL documentation, no lazy And operators are allowed in the target expression.
	 */
	@Check
	def policyRuleNoAndAllowedInTargetExpression(Policy policy) {
		genericCheckForTargetExpression(policy, And, MSG_AND_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION)
	}

	/**
	 * According to SAPL documentation, no lazy Or operators are allowed in the target expression.
	 */
	@Check
	def policyRuleNoOrAllowedInTargetExpression(Policy policy) {
		genericCheckForTargetExpression(policy, Or, MSG_OR_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION)
	}

	/**
	 * According to SAPL documentation, no lazy Or operators are allowed in the target expression.
	 */
	@Check
	def policyRuleNoAttributeFinderAllowedInTargetExpression(Policy policy) {
		genericCheckForTargetExpression(policy, AttributeFinderStep, MSG_AFS_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION)
	}

	/**
	 * looks for given class in the target expression of given Policy
	 */
	def <T extends EObject> genericCheckForTargetExpression(Policy policy, Class<T> aClass, String message) {
		val foundItem = findClass(policy.targetExpression, aClass);
		if (foundItem !== null) {
			error(message, foundItem, null)
		}
	}

	/**
	 * scan content of given EObject recursively
	 */
	def <T extends EObject> T findClass(EObject eObj, Class<T> aClass) {
		if (aClass.isInstance(eObj)) {
			return eObj as T
		}
		for (EObject o : eObj.eContents) {
			var T result = findClass(o, aClass);
			if (result !== null) {
				return result;
			}
		}
		return null;
	}

}
