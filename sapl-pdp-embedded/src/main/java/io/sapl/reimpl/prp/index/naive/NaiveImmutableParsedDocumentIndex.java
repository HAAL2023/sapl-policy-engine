package io.sapl.reimpl.prp.index.naive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import io.sapl.reimpl.prp.ImmutableParsedDocumentIndex;
import io.sapl.reimpl.prp.PrpUpdateEvent;
import io.sapl.reimpl.prp.PrpUpdateEvent.Type;
import io.sapl.reimpl.prp.PrpUpdateEvent.Update;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The Index Object has to be immutable to avoid race conditions. SAPL Objects
 * are assumed to be immutable.
 */
@Slf4j
public class NaiveImmutableParsedDocumentIndex implements ImmutableParsedDocumentIndex {
	// Mapping of Document Name to the parsed Document
	private final Map<String, SAPL> documents;

	public NaiveImmutableParsedDocumentIndex() {
		documents = new HashMap<>();
	}

	private NaiveImmutableParsedDocumentIndex(Map<String, SAPL> documents) {
		this.documents = documents;
	}

	@Override
	public Mono<PolicyRetrievalResult> retrievePolicies(AuthorizationSubscription authzSubscription,
			FunctionContext functionCtx, Map<String, JsonNode> variables) {
		VariableContext variableCtx;
		try {
			variableCtx = new VariableContext(authzSubscription, variables);
		} catch (PolicyEvaluationException e) {
			return Mono.just(new PolicyRetrievalResult(new ArrayList<>(), true));
		}
		var evaluationCtx = new EvaluationContext(functionCtx, variableCtx);
		var errorInTarget = new AtomicBoolean(false);
		return Flux.fromIterable(documents.values()).filterWhen(policy -> policy.matches(evaluationCtx))
				.onErrorContinue((t, o) -> {
					log.info("| |-- Error in target evaluation: {}", t.getMessage());
					errorInTarget.set(true);
				}).collectList().map(result -> new PolicyRetrievalResult(result, errorInTarget.get()));
	}

	@Override
	public NaiveImmutableParsedDocumentIndex apply(PrpUpdateEvent event) {
		// Do a shallow copy. String is immutable, and SAPL is assumed to be too.
		var newDocuments = new HashMap<>(documents);
		applyEvent(newDocuments, event);
		return new NaiveImmutableParsedDocumentIndex(newDocuments);
	}

	private void applyEvent(Map<String, SAPL> newDocuments, PrpUpdateEvent event) {
		for (var update : event.getUpdates()) {
			applyUpdate(newDocuments, update);
		}
	}

	private void applyUpdate(Map<String, SAPL> newDocuments, Update update) {
		var name = update.getDocument().getPolicyElement().getSaplName();
		if (update.getType() == Type.UNPUBLISH) {
			newDocuments.remove(name);
		} else {
			if (newDocuments.containsKey(name)) {
				log.error("Fatal error. Policy name collision. A document with a name ('{}') "
						+ "identical to an existing document was published to the PRP.", name);
				System.exit(1);
			}
			newDocuments.put(name, update.getDocument());
		}
	}
}
