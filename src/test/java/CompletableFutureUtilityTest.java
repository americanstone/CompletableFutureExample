import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class CompletableFutureUtilityTest {

	@Test
	void anyMatchTest() {

		List<CompletableFuture<Integer>> list= Arrays.asList(
				CompletableFuture.supplyAsync(()->5),
				CompletableFuture.supplyAsync(()->{throw new RuntimeException(); }),
				CompletableFuture.supplyAsync(()->42),
				CompletableFuture.completedFuture(0)
		);
		CompletableFutureUtility.anyMatch(list, i -> i >9)
				.thenAccept(i->System.out.println("got "+i))
				// optionally chain with:
				.whenComplete((x,t)->{ if(t!=null) t.printStackTrace(); });
	}
}