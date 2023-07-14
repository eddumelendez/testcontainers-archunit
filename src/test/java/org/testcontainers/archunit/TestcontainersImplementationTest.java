package org.testcontainers.archunit;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.containers.BigtableEmulatorContainer;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.containers.ClickHouseContainer;
import org.testcontainers.containers.CockroachContainer;
import org.testcontainers.containers.CosmosDBEmulatorContainer;
import org.testcontainers.containers.DatastoreEmulatorContainer;
import org.testcontainers.containers.Db2Container;
import org.testcontainers.containers.FirestoreEmulatorContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.InfluxDBContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.containers.OrientDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.PrestoContainer;
import org.testcontainers.containers.PubSubEmulatorContainer;
import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.containers.QuestDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.SolrContainer;
import org.testcontainers.containers.SpannerEmulatorContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.TrinoContainer;
import org.testcontainers.containers.YugabyteDBYCQLContainer;
import org.testcontainers.containers.YugabyteDBYSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.cratedb.CrateDBContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.hivemq.HiveMQContainer;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.solace.SolaceContainer;
import org.testcontainers.tidb.TiDBContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class TestcontainersImplementationTest {

	static final ArchCondition<JavaClass> notHaveDefaultPublicConstructor = new ArchCondition<JavaClass>("not have public constructor") {
		@Override
		public void check(JavaClass item, ConditionEvents events) {
			Optional<JavaConstructor> constructor = item.tryGetConstructor();
			if (constructor.isPresent() && constructor.get().getModifiers().contains(JavaModifier.PUBLIC)) {
				String message = String.format("%s has default public constructor.", item);
				events.add(SimpleConditionEvent.violated(item, message));
			}
		}
	};

	static final ArchCondition<JavaClass> haveAllowedConstructors = new ArchCondition<JavaClass>("should have constructor with String or DockerImageName arg") {
		@Override
		public void check(JavaClass item, ConditionEvents events) {
			Optional<JavaConstructor> constructorWithStringArg = item.tryGetConstructor(String.class);
			Optional<JavaConstructor> constructorWithDockerImageNameArg = item.tryGetConstructor(DockerImageName.class);
			if (constructorWithStringArg.isPresent() || constructorWithDockerImageNameArg.isPresent()) {
				String message = String.format("%s has allowed constructors.", item);
				events.add(SimpleConditionEvent.satisfied(item, message));
			}
		}
	};

	static final List<String> jdkClasses = new ClassFileImporter()
			.importPackages("java", "javax")
			.stream()
			.map(JavaClass::getName)
			.collect(Collectors.toList());

	static final Set<String> excludeMethods = Stream.of(
			"configure",
			"containerIsStarted")
			.collect(Collectors.toSet());

	static final ArchCondition<JavaClass> notExposeNonStandardTypes = new ArchCondition<JavaClass>("should not expose non-standard types") {
		@Override
		public void check(JavaClass item, ConditionEvents events) {
			Set<JavaMethod> methods = item.getMethods().stream()
					.filter(method -> !method.getReturnType().toErasure().getName().equals("void"))
					.filter(method -> !method.getReturnType().toErasure().getName().equals("boolean"))
					.filter(method -> !method.getReturnType().toErasure().getName().equals(item.getName()))
					.filter(method -> !method.getReturnType().toErasure().getName().equals("org.testcontainers.containers.JdbcDatabaseContainer"))
					.filter(method -> {
						for (String excludeMethod : excludeMethods) {
							return !method.getName().equals(excludeMethod);
						}
						return true;
					})
					.collect(Collectors.toSet());
			for (JavaMethod method : methods) {
				JavaType returnType = method.getReturnType();
				if (!jdkClasses.contains(returnType.toErasure().getName())) {
					String message = String.format("%s#%s doesn't return a native type.", item, method);
					events.add(SimpleConditionEvent.violated(item, message));
				}
			}
		}
	};

	@ParameterizedTest
	@MethodSource("containerClasses")
	void testApp(Class<?> container) {
		JavaClasses containerClass = new ClassFileImporter().importClasses(container);

		classes().should()
				.haveSimpleNameEndingWith("Container")
				.andShould()
				.beAssignableTo(GenericContainer.class)
				.andShould(notHaveDefaultPublicConstructor)
				.andShould(haveAllowedConstructors)
				.andShould(notExposeNonStandardTypes)
				.check(containerClass);
	}

	static Stream<Class<?>> containerClasses() {
		return Stream.of(
				CosmosDBEmulatorContainer.class,
				CassandraContainer.class,
				ClickHouseContainer.class,
				CockroachContainer.class,
				ConsulContainer.class,
				CouchbaseContainer.class,
				CrateDBContainer.class,
				Db2Container.class,
				ElasticsearchContainer.class,
				BigtableEmulatorContainer.class,
				DatastoreEmulatorContainer.class,
				FirestoreEmulatorContainer.class,
				PubSubEmulatorContainer.class,
				SpannerEmulatorContainer.class,
				HiveMQContainer.class,
				InfluxDBContainer.class,
				K3sContainer.class,
				KafkaContainer.class,
				LocalStackContainer.class,
				MariaDBContainer.class,
				MockServerContainer.class,
				MongoDBContainer.class,
				MSSQLServerContainer.class,
				MySQLContainer.class,
				Neo4jContainer.class,
				OracleContainer.class,
				OrientDBContainer.class,
				PostgreSQLContainer.class,
				PrestoContainer.class,
				PulsarContainer.class,
				QuestDBContainer.class,
				RabbitMQContainer.class,
				RedpandaContainer.class,
				BrowserWebDriverContainer.class,
				SolaceContainer.class,
				SolrContainer.class,
				TiDBContainer.class,
				ToxiproxyContainer.class,
				TrinoContainer.class,
				VaultContainer.class,
				YugabyteDBYCQLContainer.class,
				YugabyteDBYSQLContainer.class);
	}

}
