import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RealLifeCompletableFutureExample {

	public static void main(String[] args) {
		long start = System.currentTimeMillis();

		CompletableFuture<List<Vehicle>> job = vehicles().thenCompose(vs -> vs.stream().map(vehicle -> rating(vehicle.manufacturerId)
				.thenApply(rate -> {
					vehicle.setRating(rate);
					return vehicle;
				}))
				.collect(FuturesCollector.toFuture())
		).whenComplete((vehicles, th) -> {
			if(th == null) {
				vehicles.forEach(System.out::println);
			} else {
				throw new RuntimeException(th);
			}
		});
		job.join();

		long end = System.currentTimeMillis();

		System.out.println("Took " + (end - start) + " ms.");
	}


	static CompletableFuture<Float> rating(int manufacturer) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				simulateDelay();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(e);
			}
			switch (manufacturer) {
				case 2:
					return 4f;
				case 3:
					return 4.1f;
				case 7:
					return 4.2f;
				default:
					return 5f;
			}
		}).exceptionally(th -> -1f);
	}

	static CompletableFuture<List<Vehicle>> vehicles() {
		return CompletableFuture.supplyAsync(() -> List.of(
				new Vehicle(1, 3, "Fiesta", 2017),
				new Vehicle(2, 7, "Camry", 2014),
				new Vehicle(3, 2, "M2", 2008)
				));
	}

	private static void simulateDelay() throws InterruptedException {
		Thread.sleep(5000);
	}
}