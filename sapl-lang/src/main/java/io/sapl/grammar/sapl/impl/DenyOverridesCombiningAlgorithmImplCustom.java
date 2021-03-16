package io.sapl.grammar.sapl.impl;

import static io.sapl.api.pdp.Decision.DENY;
import static io.sapl.api.pdp.Decision.INDETERMINATE;
import static io.sapl.api.pdp.Decision.NOT_APPLICABLE;
import static io.sapl.api.pdp.Decision.PERMIT;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.Policy;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.combinators.ObligationAdviceCollector;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * This algorithm is used if a DENY decision should prevail a PERMIT without
 * setting a default decision.
 * 
 * It works as follows:
 * 
 * - If any policy document evaluates to DENY, the decision is DENY.
 * 
 * - Otherwise:
 * 
 * a) If there is any INDETERMINATE or there is a transformation uncertainty
 * (multiple policies evaluate to PERMIT and at least one of them has a
 * transformation statement), the decision is INDETERMINATE.
 * 
 * b) Otherwise:
 * 
 * i) If there is any PERMIT the decision is PERMIT.
 * 
 * ii) Otherwise the decision is NOT_APPLICABLE.
 */
@Slf4j
public class DenyOverridesCombiningAlgorithmImplCustom extends DenyOverridesCombiningAlgorithmImpl {
	
	@Override
	protected AuthorizationDecision combineDecisions(AuthorizationDecision[] decisions, boolean errorsInTarget) {
		if ((decisions == null || decisions.length == 0) && !errorsInTarget) {
			return AuthorizationDecision.NOT_APPLICABLE;
		}
		var entitlement = errorsInTarget ? INDETERMINATE : NOT_APPLICABLE;
		var collector = new ObligationAdviceCollector();
		Optional<JsonNode> resource = Optional.empty();
		for (var decision : decisions) {
			if (decision.getDecision() == DENY) {
				entitlement = DENY;
			}
			if (decision.getDecision() == INDETERMINATE) {
				if (entitlement != DENY) {
					entitlement = INDETERMINATE;
				}
			}
			if (decision.getDecision() == PERMIT) {
				if (entitlement == NOT_APPLICABLE) {
					entitlement = PERMIT;
				}
			}
			collector.add(decision);
			if (decision.getResource().isPresent()) {
				if (resource.isPresent()) {
					// this is a transformation uncertainty.
					// another policy already defined a transformation
					// this the overall result is basically INDETERMINATE.
					// However, existing DENY overrides with this algorithm.
					if (entitlement != DENY) {
						entitlement = INDETERMINATE;
					}
				} else {
					resource = decision.getResource();
				}
			}
		}
		var finalDecision = new AuthorizationDecision(entitlement, resource, collector.getObligations(entitlement),
				collector.getAdvices(entitlement));
		log.debug("| |-- {} Combined AuthorizationDecision: {}", finalDecision.getDecision(), finalDecision);
		return finalDecision;
	}

	@Override
	public Flux<AuthorizationDecision> combinePolicies(List<Policy> policies, EvaluationContext ctx) {
		return doCombinePolicies(policies, ctx);
	}
}
