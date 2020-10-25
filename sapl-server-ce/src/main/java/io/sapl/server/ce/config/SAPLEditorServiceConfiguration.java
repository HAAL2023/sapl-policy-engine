package io.sapl.server.ce.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.sapl.grammar.web.SAPLServlet;
import io.sapl.vaadin.SaplEditorConfiguration;

/**
 * Collection of initialization methods for Spring beans.
 */
@Configuration
public class SAPLEditorServiceConfiguration {
	/**
	 * Registers the bean {@link ServletRegistrationBean} with generic type
	 * {@link SAPLServlet}.
	 * 
	 * @return the value
	 */
	@Bean
	public static ServletRegistrationBean<SAPLServlet> registerXTextRegistrationBean() {
		ServletRegistrationBean<SAPLServlet> registration = new ServletRegistrationBean<>(new SAPLServlet(),
				"/xtext-service/*");
		registration.setName("XtextServices");
		registration.setAsyncSupported(true);
		return registration;
	}

	/**
	 * Registers the bean {@link SaplEditorConfiguration}.
	 * 
	 * @return the value
	 */
	@Bean
	public static SaplEditorConfiguration registerSaplEditorConfiguration() {
		SaplEditorConfiguration saplEditorConfiguration = new SaplEditorConfiguration();
		saplEditorConfiguration.setAutoCloseBrackets(true);
		saplEditorConfiguration.setHasLineNumbers(true);
		saplEditorConfiguration.setMatchBrackets(true);
		saplEditorConfiguration.setTextUpdateDelay(1);

		return saplEditorConfiguration;
	}
}
