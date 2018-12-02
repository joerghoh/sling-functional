package de.joerghoh.slingfunctional;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.sling.api.resource.LoginException;
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
	
	
	/**
	 * The purpose of this class is to illustrate how you can use Java Functional to make
	 * your code easier to understand and how you can improve the handling of unexpected cases.
	 * 
	 * 
	 * 
	 */
	
	
	final static Logger LOG = LoggerFactory.getLogger(OptionalAdapterTest.class);
	
	@Rule
	public AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);
	
	private static final String EXISTING_RESOURCE = "/content/page1/jcr:content";
	private static final String NON_EXISTING_RESOURCE = "/content/page1/jcr:content/par";
	
	
	@Before
	public void setup() {
		context.load().json(getClass().getResourceAsStream("sample-tree.json"), "/content");
		
	}
	
	
	/**
	 * This is a typical implementation you will see in many codebases; it's straight-forward,
	 * but has the obvious drawback that the Resource must exist, otherwise you'll run into a
	 * NPE.
	 */
	@Test
	public void testTraditionalCoding_noErrorHandling_DoNotCopy() {
		Resource r = context.resourceResolver().getResource(EXISTING_RESOURCE);
		String pageTitle = r.adaptTo(ValueMap.class).get("jcr:title",String.class);
		assertEquals(pageTitle,"page1");
	}
	

	/**
	 * This implementation is already improved and contains error handling for the case
	 * of the non-existing resource; but there's still the expectation, that adaptTo(ValueMap.class) 
	 * does not return null (according to the API contract it can!)
	 */
	@Test
	public void testTraditionalCoding_withErrorHandling() {
		Resource r = context.resourceResolver().getResource(EXISTING_RESOURCE);
		if (r != null) {
			String pageTitle = r.adaptTo(ValueMap.class).get("jcr:title",String.class);
			assertEquals(pageTitle,"page1");
		} else {
			String pageTitle = "defaultOnNonExistingResource";
		}
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

	/**
	 * Test the case when an exception happens underwawy
	 */
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
	
	
	
	
	
	
	
	/**
	 * This is a more complex example, where we read a property "ref" from a resource, and look it up
	 * on the repo; in case it exists we return the path of that resource otherwise a default value.
	 * 
	 * It tries to handle all error cases which might occur.
	 */
	@Test
	public void testResolveReference_Java7CodingStyle() {
		String name = "/content/pathNotFound";
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
	
	
	
	/**
	 * The following testcases implements the same function but uses functional Java 8 style, Optionals and the 
	 * withResourceResolver() function.
	 * It obviously has some advantages:
	 * - no explicit handling for nulls or exceptions
	 * - exception handling is splitted from the dealing with null-values 
	 *  
	 * Con:
	 * - an explicit handling for the NON_EXISTING_RESOURCE is not possible with this version of withResourceResolver;
	 *   but enhancing it to handle this case also isn't a big problem.
	 */

	
	@Test
	public void testResolveReference_Functional() {
		
		String name = withResourceResolver(
				rr -> Optional.ofNullable(rr.getResource("/content/page2/jcr:content"))
					.map((Resource res) -> res.adaptTo(ValueMap.class))
					.map(vm -> vm.get("ref",String.class))
					.map(ref -> rr.getResource(ref))
					.map(reference -> reference.getPath())
					.orElseGet(() -> "defaultOnNotFound"),
				e -> withDefaultValue(e,"defaultOnException"));
		assertEquals ("/content/page3",name );
	}
	
	@Test
	public void testResolveReference_Functional_WithNicerNames() {
		
		String name = withResourceResolver(
				rr -> resource(rr,"/content/page2/jcr:content")
					.map((Resource res) -> res.adaptTo(ValueMap.class))
					.map(vm -> vm.get("ref",String.class))
					.map(ref -> rr.getResource(ref))
					.map(reference -> reference.getPath())
					.orElseGet(() -> "defaultOnNotFound"),
				e -> withDefaultValue(e,"defaultOnException"));
		assertEquals ("/content/page3",name );
	}
	
//	@Test
//	public void test_Functional_CountNodes() {
//		
//		final Function<Resource,List<Resource>> getChildren = res -> {
//			List<Resource> result = new ArrayList<Resource>();
//			res.listChildren().forEachRemaining(child->result.addAll(getChildren(child)));
//			return result;
//		};
//		
////		Integer count = withResourceResolver(
////				rr -> resource(rr,"/content")
////				.flatMap(mapper)
////				);
//	}

	
	/**
	 * The helper functions we need.
	 */
	
	
	private <T> T withResourceResolver(final Function<ResourceResolver,T> onSuccess, final Function<Exception,T> onError) {
		return withResourceResolver(adminResolver,onSuccess,onError);
	}
	
	
	/**
	 * A convenience function which executes a function on a ResourceResolver; it takes care of the livecycle of the ResourceResolver
	 * @param resolverCreator a function supplying a new ResourceResolver; the ResourceResolver is closed afterwards
	 * @param onSuccess the method which is executed
	 * @param onError error handling in case of any exception
	 * @return
	 */
	private <T> T withResourceResolver(final Supplier<ResourceResolver> resolverCreator, final Function<ResourceResolver,T> onSuccess, final Function<Exception,T> onError) {
		
		try (ResourceResolver resolver = resolverCreator.get()) {
			T result = onSuccess.apply(resolver);
			return result;
		} catch (Exception e) {
			return onError.apply(e);
		}
	}
	
	
	private Supplier<ResourceResolver> adminResolver = () -> {
		ResourceResolverFactory rrf = context.getService(ResourceResolverFactory.class);
		try {
			return rrf.getAdministrativeResourceResolver(null);
		} catch (LoginException e) {
			throw new RuntimeException("Cannot open admin session",e);
		}
	};
	
	 
	 private <T> T withDefaultValue(Exception e, T defaultValue) {
		 return defaultValue;
	 }
	
	
	 private Optional<Resource> resource(ResourceResolver resolver, String path) {
		 return Optional.ofNullable(resolver.getResource(path));
	 }
	

	
}
