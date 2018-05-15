package de.joerghoh.slingfunctional;
import static org.junit.Assert.assertEquals;

import java.util.Optional;
import java.util.function.Function;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.wcm.testing.mock.aem.junit.AemContext;

@RunWith(MockitoJUnitRunner.class)
public class OptionalAdapterTest {
	
	final static Logger LOG = LoggerFactory.getLogger(OptionalAdapterTest.class);
	
	@Rule
	public AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);
	
	private static final String EXISTING_RESOURCE = "/content/page1/jcr:content";
	private static final String NON_EXISTING_RESOURCE = "/content/page1/jcr:content/par";
	
	
	@Before
	public void setup() {
		context.load().json(getClass().getResourceAsStream("sample-tree.json"), "/content");
		
	}
	
	
	@Test
	public void testTraditionalCoding	() {
		Resource r = context.resourceResolver().getResource(EXISTING_RESOURCE);
		assertEquals(r.adaptTo(ValueMap.class).get("jcr:title"),"page1");
	}
	
	
	@Test
	public void testDefaultImplementation_Functional() {
		
		String name = withMockResourceResolver(
				r -> Optional.ofNullable(r.getResource(EXISTING_RESOURCE))
					.flatMap(res -> Optional.ofNullable(res.adaptTo(ValueMap.class)))
					.flatMap(v -> Optional.ofNullable(v.get("jcr:title",String.class)))
					.orElseGet(() -> "defaultOnNotFound"),
				e -> withDefaultValue(e,"defaultOnException"));
		assertEquals ("page1",name);
		
	}
	
	@Test
	public void testDefaultImplementationWithFunctional_NonExistingResource() {
		
		String name = withMockResourceResolver(
				r -> Optional.ofNullable(r.getResource(NON_EXISTING_RESOURCE))
					.flatMap(res -> Optional.ofNullable(res.adaptTo(ValueMap.class)))
					.flatMap(v -> Optional.ofNullable(v.get("jcr:title",String.class)))
					.orElseGet(() -> "defaultText"),
				e -> withDefaultValue(e,"defaultOnException"));
		assertEquals ("defaultText",name );
		
	}

	
	@Test
	public void testDefaultImplementation_Functional_WithLoginException() {
		
		Function<Resource,Optional<Resource>> functionThrowingException = (param) -> 
		{
			throw new RuntimeException("blub");
			//return Optional.ofNullable(param);
		};
		
		String name = withMockResourceResolver(
				r -> Optional.ofNullable(r.getResource(EXISTING_RESOURCE))
					.flatMap(functionThrowingException)
					.flatMap(res -> Optional.ofNullable(res.adaptTo(ValueMap.class)))
					.flatMap(v -> Optional.ofNullable(v.get("jcr:title",String.class)))
					.orElseGet(() -> "defaultText"),		
				e -> withDefaultValue(e,"defaultOnException"));
		assertEquals ("defaultOnException", name);	
	}
	

	@Test
	public void testDefaultImplementationWithFunctional_IndirectLookup() {
		
		String name = withMockResourceResolver(
				r -> Optional.ofNullable(r.getResource(NON_EXISTING_RESOURCE))
					.flatMap(res -> Optional.ofNullable(res.adaptTo(ValueMap.class)))
					.flatMap(v -> Optional.ofNullable(v.get("jcr:title",String.class)))
					.orElseGet(() -> "defaultText"),
				e -> withDefaultValue(e,"defaultOnException"));
		assertEquals ("defaultText",name );
		
	}
	
	
	private <T> T withMockResourceResolver(final Function<ResourceResolver,T> onSuccess, 
			final Function<Exception,T> onError) {

		ResourceResolverFactory rrf = context.getService(ResourceResolverFactory.class);
		try (ResourceResolver resolver = rrf.getAdministrativeResourceResolver(null)) {
			T result =  onSuccess.apply(resolver);
			return result;			

		} catch (Exception e) {
			return onError.apply(e);
		} 
	}
	
	 
	 private <T> T withDefaultValue(Exception e, T defaultValue) {
		 return defaultValue;
	 }
	
	 private <T> T throwing(Exception e, T defaultValue) {
		 throw new RuntimeException (e);
	 }
	
	
	

	
}
