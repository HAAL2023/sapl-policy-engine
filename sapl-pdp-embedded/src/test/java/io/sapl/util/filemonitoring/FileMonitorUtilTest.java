package io.sapl.util.filemonitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.File;
import java.nio.file.NoSuchFileException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@Timeout(3)
class FileMonitorUtilTest {

	@Test
	void return_valid_textfile_as_string() throws Exception {
		var validTextFile = new File("src/test/resources/policies/policy_1.sapl");
		var fileAsString = FileMonitorUtil.readFile(validTextFile);

		assertThat(fileAsString).isNotEmpty();
		assertThat(fileAsString).containsIgnoringCase("policy 1");
	}

	@Test
	void throw_exception_when_reading_non_existent_file() throws Exception {
		var missingFile = new File("src/test/resources/not_existing.txt");

		assertThatExceptionOfType(NoSuchFileException.class).isThrownBy(() -> FileMonitorUtil.readFile(missingFile));
	}

	@Test
	void resolve_home_folder_in_valid_path() {
		var props = System.getProperties();
		var homePath = String.format("%shome%sjohndoe", File.separator, File.separator);
		props.setProperty("user.home", String.format("%shome%sjohndoe", File.separator, File.separator));

		assertThat(FileMonitorUtil.resolveHomeFolderIfPresent("~" + File.separator)).isNotEmpty().contains(homePath);

		assertThat(FileMonitorUtil.resolveHomeFolderIfPresent("~/")).isNotEmpty().contains(homePath);
	}

	@Test
	void return_no_event_for_non_existent_directory() throws Exception {
		Flux<FileEvent> monitorFlux = FileMonitorUtil.monitorDirectory("src/test/resources/not_existing_dir",
				__ -> true);

		StepVerifier.create(monitorFlux).expectNextCount(0L).thenCancel().verify();

	}

	@Test
	void return_no_event_when_nothing_changes() throws Exception {
		Flux<FileEvent> monitorFlux = FileMonitorUtil.monitorDirectory("src/test/resources/policies", __ -> true);
		StepVerifier.create(monitorFlux).expectNextCount(0L).thenCancel().verify();
	}
}
