import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class ExecutionOrder {

	public static void main(String[] args) {

		ExecutorService executor = Executors.newCachedThreadPool();

		Function<List<Integer>, CompletableFuture<List<Vehicle>>> fetchVehicle1 = ids -> {
			System.out.println("fetching vehicle1 blocking 500" + Thread.currentThread().getName());
			sleep(500);

			Supplier<List<Vehicle>> supplier = () -> ids.stream().map(Vehicle::new).collect(toList());

			return CompletableFuture.supplyAsync(supplier);
		};

		Function<List<Integer>, CompletableFuture<List<Vehicle>>> fetchVehicle2 = ids -> {
			System.out.println("fetching vehicle2 blocking 500" + Thread.currentThread().getName());
			sleep(5000);

			Supplier<List<Vehicle>> supplier = () -> ids.stream().map(Vehicle::new).collect(toList());

			return CompletableFuture.supplyAsync(supplier);
		};

		Consumer<List<Vehicle>> displayer = vehicles -> vehicles.forEach(System.out::println);

		CompletableFuture<List<Integer>> cf = CompletableFuture.supplyAsync(() -> Arrays.asList(1,2,3,5));

//		CompletableFuture<List<Vehicle>> vehicle1 = cf.thenCompose(fetchVehicle1);  // who is executed first?
//		CompletableFuture<List<Vehicle>> vehicle2 = cf.thenCompose(fetchVehicle2);

		CompletableFuture<List<Vehicle>> vehicle1 = cf.thenComposeAsync(fetchVehicle1,executor);  // who is executed first?
		CompletableFuture<List<Vehicle>> vehicle2 = cf.thenComposeAsync(fetchVehicle2, executor);

		vehicle1.thenRun(() -> System.out.println("after fetching vehicle1 then run print"));
		vehicle2.thenRun(() -> System.out.println("after fetching vehicle2 then run print"));

		// Dependencies among stages control the triggering of computations, but do not otherwise guarantee any particular ordering.
		vehicle1.acceptEither(vehicle2, displayer);

		 sleep(10_000);

		 executor.shutdown();
	}


	public static void sleep(long millis){
		try {
			Thread.sleep(millis);
		} catch(InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}