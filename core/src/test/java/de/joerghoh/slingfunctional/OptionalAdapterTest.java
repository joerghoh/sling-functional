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
	public void testTraditionalCoding_noErrorHandling_DoNotCopy() {
		Resource r = context.resourceResolver().getResource(EXISTING_RESOURCE);
		String pageTitle = r.adaptTo(ValueMap.class).get("jcr:title",String.class);
		assertEquals(pageTitle,"page1");
	}
	

	
	
	@Test
	public void testDefaultImplementation_Functional() {
		
		String name = withResourceResolver(
				r -> Optional.ofNullable(r.getResource(EXISTING_RESOURCE))
					.map(res -> res.adaptTo(ValueMap.class))
					.map(v -> v.get("jcr:title",String.class))
					.orElseGet(() -> "defaultOnNotFound"),
				e -> withDefaultValue(e,"defaultOnException"));
		assertEquals ("page1",name);
		
	}
	
	@Test
	public void testDefaultImplementation_Functional_NonExistingResource() {
		
		String name = withResourceResolver(
				r -> Optional.ofNullable(r.getResource(NON_EXISTING_RESOURCE))
					.map(res -> res.adaptTo(ValueMap.class))
					.map(v -> v.get("jcr:title",String.class))
					.orElseGet(() -> "defaultText"),
				e -> withDefaultValue(e,"defaultOnException"));
		assertEquals ("defaultText",name );
		
	}

	
	@Test
	public void testDefaultImplementation_Functional_WithLoginException() {
		
		Function<Resource,Resource> functionThrowingException = (param) -> 
		{
			throw new RuntimeException("blub");
			//return Optional.ofNullable(param);
		};
		
		String name = withResourceResolver(
				r -> Optional.ofNullable(r.getResource(EXISTING_RESOURCE))
					.map(functionThrowingException)
					.map(res -> res.adaptTo(ValueMap.class))
					.map(v -> v.get("jcr:title",String.class))
					.orElseGet(() -> "defaultText"),		
				e -> withDefaultValue(e,"defaultOnException"));
		assertEquals ("defaultOnException", name);	
	}
	
	@Test
	public void testResolveReference_Java7CodingStyle() {
		String name = "defaultOnNotFound";
		ResourceResolverFactory rrf = context.getService(ResourceResolverFactory.class);
		try (ResourceResolver rr = rrf.getAdministrativeResourceResolver(null)) {
			Resource res = rr.getResource("/content/page2/jcr:content");
			if (res != null) {
				ValueMap vm = res.adaptTo(ValueMap.class);
				if (vm != null) {
					String ref = vm.get("ref",String.class);
					if (ref != null) {
						Resource reference = rr.getResource(ref);
						if (reference != null) {
							name = reference.getPath();
						}
					}
				}
			}
		} catch (Exception e) {
			name="defaultOnException";
		}
		assertEquals("/content/page3",name);
	}
	
	@Test
	public void testResolveReference_Functional() {
		
		String name = withResourceResolver(
				rr -> Optional.ofNullable(rr.getResource("/content/page2/jcr:content"))
					.map(res -> res.adaptTo(ValueMap.class))
					.map(vm -> vm.get("ref",String.class))
					.map(ref -> rr.getResource(ref))
					.map(reference -> reference.getPath())
					.orElseGet(() -> "defaultOnNotFound"),
				e -> withDefaultValue(e,"defaultOnException"));
		assertEquals ("/content/page3",name );
	}
	


	
	
	private <T> T withResourceResolver(final Function<ResourceResolver,T> onSuccess, 
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
