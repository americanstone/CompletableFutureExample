import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 *  Trying to understand some of behaviors of CompletableFuture
 *
 *  the execution order conclusions:
 *
 *  the chain of completableFuture execution order can be guaranteed.
 *  *Async chain and main thread execution order can be guaranteed.
 *  *Async and non-Async mix chain and main thread execution is kind of random
 *  which non-Async can be executed in main thread potentially can block the main thread.
 *  block or not depends on non-Async's previous stage completed or not
 *  if completed, non-Async task is executed in main thread, otherwise in previous stage thread
 *
 */



public class CompletedFuturePlaygroud {
	 ExecutorService executor = Executors.newFixedThreadPool(3, new ThreadFactory() {
		int count = 1;
		@Override
		public Thread newThread(Runnable runnable) {
			return new Thread(runnable, "custom-executor-" + count++);
		}
	});
	 Random random = new Random();

	@Test
	public void completedFutureExample() {
			CompletableFuture cf = CompletableFuture.completedFuture("message");
			assertTrue(cf.isDone());
			assertEquals("message", cf.getNow(null));
	}

	@Test
	public void runAsyncExample() {
		CompletableFuture cf = CompletableFuture.runAsync(() -> {
			assertTrue(Thread.currentThread().isDaemon());
			randomSleep();
		});
		assertFalse(cf.isDone());
		sleepEnough();
		assertTrue(cf.isDone());
	}

	@Test
	// if the previous stage completed theApply is executed in caller thread(this case main thread)
	// otherwise, it is executed as same thread in previous stage
	public void thenApplyExample() {
		CompletableFuture cf = CompletableFuture.completedFuture("message")
				.thenApply(s -> {

							assertEquals("main", Thread.currentThread().getName());

							assertFalse(Thread.currentThread().isDaemon());
					System.out.println(Thread.currentThread().getName() + " inner");
							return s.toUpperCase();
				});
		System.out.println(Thread.currentThread().getName() + " outer");
		assertTrue(cf.isDone());
		assertEquals("MESSAGE", cf.getNow(null));
	}

	@Test
	public void thenApplyAsyncExample() {
		CompletableFuture cf = CompletableFuture.completedFuture("message")
				.thenApplyAsync(s -> {
					assertTrue(Thread.currentThread().isDaemon());

					assertNotEquals("main", Thread.currentThread().getName());
					
					randomSleep();
					return s.toUpperCase();
				});
		assertFalse(cf.isDone());
		assertNull(cf.getNow(null));
		assertEquals("MESSAGE", cf.join());
	}

	// if the previous stage completed theApply is executed in caller thread(this case main thread)
	// otherwise, it is executed as same thread in previous stage
	// example2 vs example3
	@Test
	public void thenApplyExample2(){
		CompletableFuture<String> cf = supplyAsync(() -> {
			// completed before next stage
			System.out.println(Thread.currentThread().getName());
			return "message";
		}).thenApply(s -> {
			// previous stage was completed, execute in main
			assertEquals("main", Thread.currentThread().getName());
			randomSleep();
			return s.toUpperCase();
		});
		System.out.println("main thread!");
		assertTrue(cf.isDone());
		System.out.println(cf.join());
	}

	@Test
	public void thenApplyExample3(){
		CompletableFuture<String> cf = supplyAsync(() -> {

			randomSleep();
			System.out.println(Thread.currentThread().getName());
			return "message";
		}).thenApply(s -> {
			// previous stage was not completed, execute in pool thread
			assertNotEquals("main", Thread.currentThread().getName());
			randomSleep();
			return s.toUpperCase();
		});
		System.out.println("main thread!");
		assertFalse(cf.isDone());
		System.out.println(cf.join());
	}

	// if in the chain has non Async call, the sync task can potentially block the main thread.
	//  whether it block or not, dependents on its previous stage completion.
	//  if previous stage was not completed, it runs in same thread of previous stage w/o block main(thenApplyExample3)
	//  otherwise, it run in main thread (thenApplyAsyncSyncMixExample)
	@Test
	public void thenApplyAsyncSyncMixExample(){
		CompletableFuture<Void> cf = supplyAsync(() -> {
			System.out.println(Thread.currentThread().getName());
			return "message";
		}).thenApplyAsync(s -> {
			System.out.println(Thread.currentThread().getName());
			return s.toUpperCase();
		}).thenRun(() -> {
			System.out.println(Thread.currentThread().getName());
			randomSleep();
			randomSleep();
			randomSleep();
			randomSleep();randomSleep();
			randomSleep();randomSleep();
			randomSleep();
			assertEquals("main", Thread.currentThread().getName());
		});

		assertTrue(cf.isDone());

		System.out.println("main thread!");
		System.out.println(cf.join());
	}

	@Test
	public void if_previous_stage_completed_thenApplyAsync_chain_executed_in_same_pool_thread(){
		CompletableFuture<String> cf = supplyAsync(() ->

				Thread.currentThread().getName()

		).thenApplyAsync(s -> {

			assertEquals(Thread.currentThread().getName(), s);
			randomSleep();
			return Thread.currentThread().getName();

		}).thenApplyAsync(y -> {
			assertEquals(Thread.currentThread().getName(), y);

			return Thread.currentThread().getName();
		});

		System.out.println("main thread!");
		assertFalse(cf.isDone());
		System.out.println(cf.join());
	}

	// why example4 and example5 have different execution order?
	// the main thread and pool threads execution order is not guaranteed in *Async
	@Test
	public void thenApplyAsyncExample4(){
		CompletableFuture<Void> cf = supplyAsync(() -> {
			assertNotEquals("main", Thread.currentThread().getName());
			return "message";
		}).thenApplyAsync(s -> {

			randomSleep();
			
			assertNotEquals("main", Thread.currentThread().getName());

			return s.toUpperCase();
		}).thenAccept(y -> {
			// previous stage hasn't completed
			assertNotEquals("main", Thread.currentThread().getName());

		});

		System.out.println("main thread completed first!");
		assertFalse(cf.isDone());
		System.out.println(cf.join());
	}

	@Test
	public void thenApplyAsyncExample4_5(){
		CompletableFuture<Void> cf = supplyAsync(() -> {
			assertNotEquals("main", Thread.currentThread().getName());
			return "message";
		}).thenApplyAsync(s -> {

			// previous stage completed

			assertNotEquals("main", Thread.currentThread().getName());

			return s.toUpperCase();
		}).thenAccept(y -> {

			assertEquals("main", Thread.currentThread().getName());

		});

		System.out.println("main thread completed first!");
		assertTrue(cf.isDone());
		System.out.println(cf.join());
	}

	@Test
	public void thenApplyAsyncExample5(){
		CompletableFuture<Void> cf = supplyAsync(() -> {
			System.out.println(Thread.currentThread().getName() + " supplyAsync");
			return "message";
		}).thenApplyAsync(s -> {
			System.out.println(Thread.currentThread().getName()+ " thenApplyAsync");
			randomSleep();
			return s.toUpperCase();
		}).thenAcceptAsync(y -> {
			System.out.println(Thread.currentThread().getName()+ " last thenApplyAsync");
		});

		System.out.println("main thread!");
		assertFalse(cf.isDone());
		System.out.println(cf.join());
	}

	// the chain of completableFuture execution order can be guaranteed but the chain and main thread
	// execution order is kind of random(thenApplyExample3)
	@Test
	public void thenApplyAsyncExample6(){
		CompletableFuture<String> cf = supplyAsync(() -> {
			randomSleep();
			System.out.println(Thread.currentThread().getName());

			return Thread.currentThread().getName();
		}).thenApplyAsync(s -> {
			assertEquals(s,Thread.currentThread().getName());
			randomSleep();
			return Thread.currentThread().getName();
		}).whenComplete((y ,ex) -> {
			assertEquals(y,Thread.currentThread().getName());
			System.out.println(Thread.currentThread().getName());
		});

		System.out.println("main thread!");
		assertFalse(cf.isDone());
		System.out.println(cf.join());
	}


	@Test
	public void thenApplyAsyncExample7(){
		CompletableFuture<Void> cf = supplyAsync(() -> {
			System.out.println(Thread.currentThread().getName() + " first");
			randomSleep();
			randomSleep();
			return Thread.currentThread().getName();
		}).thenApplyAsync(s -> {
			assertEquals(s,Thread.currentThread().getName());
			System.out.println(Thread.currentThread().getName() + " second");
			randomSleep();
			randomSleep();
			return Thread.currentThread().getName();
		}).thenRun(() -> {
			assertNotEquals("main",Thread.currentThread().getName());
			System.out.println(Thread.currentThread().getName() + " last");
		});

		System.out.println("main thread!");
		assertFalse(cf.isDone());
		System.out.println(cf.join());
	}

	// should nested Async?
	@Test
	public void thenApplyAsyncExample8(){
		CompletableFuture<Void> cf = supplyAsync(() -> {
			System.out.println(Thread.currentThread().getName());
			return "message";
		}).thenApplyAsync(s -> {
			CompletableFuture<Void> secondStage = CompletableFuture.runAsync(() -> {
//				randomSleep();
//				randomSleep();
				System.out.println(Thread.currentThread().getName() + " second");
			});

			return secondStage;
		}).thenAccept((ss ) -> {
			assertFalse(ss.isDone());
			System.out.println(Thread.currentThread().getName() + " last");
		});

		System.out.println("main thread!");
		//assertFalse(cf.isDone());
		System.out.println(cf.join());
	}

	@Test
	public void thenApplyAsyncWithExecutorExample() {
		CompletableFuture cf = CompletableFuture.completedFuture("message").thenApplyAsync(s -> {
			assertTrue(Thread.currentThread().getName().startsWith("custom-executor-"));
			assertFalse(Thread.currentThread().isDaemon());
			randomSleep();
			return s.toUpperCase();
		}, executor);
		assertFalse(cf.isDone());
		assertNull(cf.getNow(null));
		assertEquals("MESSAGE", cf.join());
	}

	@Test
	public void thenAcceptExample() {
		StringBuilder result = new StringBuilder();
		CompletableFuture<Void> cf = CompletableFuture.completedFuture("thenAccept message")
				.thenAccept(s -> {
					System.out.println(Thread.currentThread().getName());
					result.append(s);
				});
		System.out.println(Thread.currentThread().getName());
		assertTrue(cf.isDone());
		assertTrue("Result was not empty", result.length() > 0);
	}


	@Test
	public void thenAcceptAsyncExample() {
		StringBuilder result = new StringBuilder();
		CompletableFuture<Void> cf = CompletableFuture.completedFuture("thenAcceptAsync message")
				.thenAcceptAsync(s -> result.append(s));
		cf.join();
		assertTrue("Result was not empty", result.length() > 0);
	}

	@Test
	public void cancelExample() {
		CompletableFuture<String> delayedAction = CompletableFuture.completedFuture("message")
				.thenApplyAsync(
						String::toUpperCase,
						CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)
				);
//		The exceptionally(Function<Throwable, T> function) call is the simplest one. It returns a CompletionStage that will complete
//	    normally if the upstream CompletionStage also completes normally. The result returned is the same as the result of the upstream  CompletionStage.
//		On the other hand, if this upstream CompletionStage raises an exception,
//		this exception is passed to the provided function. The returned CompletionStage then completes normally returning
//		the result of the provided function. There is no asynchronous version of this method.

		CompletableFuture<String> cf2 = delayedAction.exceptionally(throwable -> "canceled message");

		assertTrue("If not already completed, completes this CompletableFuture with a CancellationException",
				delayedAction.cancel(true));

		assertTrue("Was not completed exceptionally", delayedAction.isCompletedExceptionally());

		assertEquals("canceled message", cf2.join());
	}

	@Test
	public void applyToEitherExample() {
		String original = "Message";
		CompletableFuture<String> delayedUpperCase = CompletableFuture.completedFuture(original)
				.thenApplyAsync(s -> delayedUpperCase(s));

		CompletableFuture<String> delayedLowerCase = CompletableFuture.completedFuture(original)
				.thenApplyAsync(s -> delayedLowerCase(s));

		CompletableFuture<String> either = delayedUpperCase.applyToEither(
											delayedLowerCase,
											s -> s + " from applyToEither");
		String result = either.join();
		System.out.println("result " + result);
		assertTrue(result.endsWith(" from applyToEither"));
	}

	@Test
	public void acceptEitherExample() {
		String original = "Message";
		CompletableFuture<String> delayedUpperCase = CompletableFuture.completedFuture(original)
				.thenApplyAsync(s -> delayedUpperCase(s));

		CompletableFuture<String> delayedLowerCase = CompletableFuture.completedFuture(original)
				.thenApplyAsync(s -> delayedLowerCase(s));

		StringBuilder result = new StringBuilder();

		CompletableFuture<Void> cf = delayedUpperCase.acceptEither(
										delayedLowerCase,
										s -> result.append(s).append("acceptEither")
		);
		
		cf.join();
		System.out.println();
		assertTrue("Result was empty", result.toString().endsWith("acceptEither"));
	}
	@Test
	public void runAfterBothExample() throws ExecutionException, InterruptedException {
		String original = "Message";
		StringBuilder result = new StringBuilder();
		CompletableFuture<String> delayedUpperCase = CompletableFuture.completedFuture(original)
				.thenApplyAsync(s -> delayedUpperCase(s));
		CompletableFuture<String> delayedLowerCase = CompletableFuture.completedFuture(original)
				.thenApplyAsync(s -> delayedLowerCase(s));

		delayedUpperCase.runAfterBoth(
						delayedLowerCase,
						() -> result.append("done")
				).get();
		assertTrue("Result was empty", result.length() > 0);
	}


	@Test
	public void thenAcceptBothExample() throws ExecutionException, InterruptedException {
		String original = "Message";
		StringBuilder result = new StringBuilder();
		CompletableFuture<String> delayedUpperCase = CompletableFuture.completedFuture(original)
				.thenApplyAsync(s -> delayedUpperCase(s));
		CompletableFuture<String> delayedLowerCase = CompletableFuture.completedFuture(original)
				.thenApplyAsync(s -> delayedLowerCase(s));

				delayedUpperCase
				.thenAcceptBoth(
						delayedLowerCase,
						(s1, s2) -> result.append(s1 + s2)
				).get();
		assertEquals("MESSAGEmessage", result.toString());
	}

	@Test
	public void thenCombineExample() throws ExecutionException, InterruptedException {
		String original = "Message";
		CompletableFuture<String> delayedUpperCase = CompletableFuture.completedFuture(original)
				.thenApplyAsync(s -> delayedUpperCase(s));

		CompletableFuture<String> delayedLowerCase = CompletableFuture.completedFuture(original)
				.thenApplyAsync(s -> delayedLowerCase(s));


		CompletableFuture<String> cf = delayedUpperCase
				.thenCombine(delayedLowerCase,
						(s1, s2) -> s1 + s2);
		cf.get();
		assertEquals("MESSAGEmessage", cf.getNow(null));
	}

	@Test
	public void thenCombineAsyncExample() {
		String original = "Message";
		CompletableFuture<String> delayedUpperCase = CompletableFuture.completedFuture(original)
				.thenApplyAsync(s -> delayedUpperCase(s));

		CompletableFuture<String> delayedLowerCase = CompletableFuture.completedFuture(original)
				.thenApplyAsync(s -> delayedLowerCase(s));


		CompletableFuture<String> cf = delayedUpperCase
				.thenCombine(delayedLowerCase,
						(s1, s2) -> s1 + s2);
		assertEquals("MESSAGEmessage", cf.join());
	}
	@Test
	public void thenComposeExample() {
		/**
		 * We can also compose the elements instead of chaining them. This makes sense only for tasks that take the result of the
		 * previous task and provide an object wrapped in another CompletableFuture.
		 * This is once more a one-to-one relation (not chaining, because this is composition)
		 */
		String original = "Message";
		CompletableFuture<String> delayedUpperCase = CompletableFuture.completedFuture(original)
				.thenApplyAsync(s -> delayedUpperCase(s));

		CompletableFuture<String> delayedLowerCase = CompletableFuture.completedFuture(original)
				.thenApplyAsync(s -> delayedLowerCase(s));

		CompletableFuture<String> cf = delayedUpperCase.thenCompose(upper -> delayedLowerCase
						.thenApply(s -> upper + s));
		assertEquals("MESSAGEmessage", cf.join());
	}

	/**
	 *
	 * anyOfExample vs anyOfExample2
	 *
	 *   always block the anyOf CompletableFuture to get the result
	 */
	@Test
	public void anyOfExample() throws ExecutionException, InterruptedException {
		StringBuilder result = new StringBuilder();
		List<String> messages = List.of("a", "b", "c");

		Function<String, CompletableFuture<String>> delayedUpperCase = (msg) -> CompletableFuture.completedFuture(msg)
				.thenApplyAsync(s -> delayedUpperCase(s));

		List<CompletableFuture<String>> upperCaseFutures = messages.stream()
				.map(delayedUpperCase)
				.collect(Collectors.toList());

		CompletableFuture<Object> any = CompletableFuture.anyOf(
				upperCaseFutures.toArray(new CompletableFuture[upperCaseFutures.size()])
		).whenComplete((res, th) -> {
			if(th == null) {
				assertTrue(isUpperCase((String) res));
				result.append(res);
			}
		});

		assertFalse(any.isDone());

		any.get();// block until done

		assertTrue("Result was empty", result.length() > 0);
	}

	@Test
	public void anyOfExample2() throws ExecutionException, InterruptedException {
		StringBuilder result = new StringBuilder();
		List<String> messages = List.of("a", "b", "c");
		List<CompletableFuture<String>> upperCaseFutures = messages.stream()
															.map(msg -> CompletableFuture.completedFuture(msg).thenApply(s -> delayedUpperCase(s)))
															.collect(Collectors.toList());

		CompletableFuture<Object> any = CompletableFuture.anyOf(
				upperCaseFutures.toArray(new CompletableFuture[upperCaseFutures.size()])
		).whenComplete((res, th) -> {
			if(th == null) {
				assertTrue(isUpperCase((String) res));
				result.append(res);
			}
		});

		assertTrue(any.isDone());

		any.get();//
		assertTrue("Result was empty", result.length() > 0);
	}

	@Test
	public void anyOf_with_condition() throws ExecutionException, InterruptedException {
		List<String> messages = List.of("a", "b", "c");
		Function<String, CompletableFuture<String>> delayedUpperCase = (msg) -> CompletableFuture.completedFuture(msg)
				.thenApplyAsync(s -> delayedUpperCase(s));

		List<CompletableFuture<String>> upperCaseFutures = messages.stream()
				.map(delayedUpperCase)
				.collect(Collectors.toList());

		List<CompletableFuture<String>> proxy = upperCaseFutures.stream()
				.map(upperCaseFuture -> {
					CompletableFuture<String> futureWithCheck = new CompletableFuture<>();
					upperCaseFuture.thenAccept(upperCase -> {
						// incomplete CompletableFuture
						if(upperCase.equals("B")) {
							// complete if only if the result equals b
							futureWithCheck.complete(upperCase);
						}
					});
					
					return futureWithCheck;
				})
				.collect(Collectors.toList());

		// only want to any of CompletableFuture return value equals "b" trigger the whenComplete
		CompletableFuture<Object> any = CompletableFuture.anyOf(proxy.toArray(new CompletableFuture[proxy.size()]))
				.whenComplete((res, th) -> {
					if(th == null) {
						assertTrue("B".equals((String) res));
					}
				});
		String result =(String) any.get();

		assertEquals("B", result);
	}

	@Test
	public void anyOf_with_condition_2() throws ExecutionException, InterruptedException {
		List<String> messages = List.of("a", "b", "c");
		Function<String, CompletableFuture<String>> delayedUpperCase = (msg) -> CompletableFuture.completedFuture(msg)
				.thenApplyAsync(s -> delayedUpperCase(s));

		List<CompletableFuture<String>> upperCaseFutures = messages.stream()
				.map(delayedUpperCase)
				.collect(Collectors.toList());

		List<CompletableFuture<String>> proxy = upperCaseFutures
				.stream()
				.map(upperCaseFuture -> upperCaseFuture
										.thenCompose(upperCase -> upperCase.equals("B") ? CompletableFuture.completedFuture(upperCase) : new CompletableFuture<>()))
				.collect(Collectors.toList());

		// only want to any of CompletableFuture return value equals "b" trigger the whenComplete
		CompletableFuture<Object> any = CompletableFuture.anyOf(proxy.toArray(new CompletableFuture[proxy.size()]))
				.whenComplete((res, th) -> {
					if(th == null) {
						System.out.println(res);
						assertTrue("B".equals((String) res));
					}
				});
		String result = (String)any.get();
		assertEquals("B", result);
	}

	@Test
	public void allOfExample() {
		StringBuilder result = new StringBuilder();
		List<String> messages = List.of("a", "b", "c");
		List<CompletableFuture<String>> futures = messages.stream()
				.map(msg -> CompletableFuture.completedFuture(msg).thenApply(s -> delayedUpperCase(s)))
				.collect(Collectors.toList());

		CompletableFuture
				.allOf(futures.toArray(new CompletableFuture[futures.size()]))
				.whenComplete((v, th) -> {
											futures.forEach(cf -> assertTrue(isUpperCase(cf.getNow(null))));
											result.append("done");
				});

		assertTrue("Result was empty", result.length() > 0);
	}

	@Test
	public void allOfAsyncExample() {
		StringBuilder result = new StringBuilder();
		List<String> messages = Arrays.asList("a", "b", "c");
		List<CompletableFuture<String>> futures = messages.stream()
				.map(msg -> CompletableFuture.completedFuture(msg).thenApplyAsync(s -> delayedUpperCase(s)))
				.collect(Collectors.toList());
		CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]))
				.whenComplete((v, th) -> {
					futures.forEach(cf -> assertTrue(isUpperCase(cf.getNow(null))));
					result.append("done");
				});
		allOf.join();
		assertTrue("Result was empty", result.length() > 0);
	}

	@Test
	public void completeAfterCompletionExample(){
		ExecutorService executor = Executors.newCachedThreadPool();
		Supplier<String> delayedSupplier = () -> {
			try {
				Thread.sleep(500);
			} catch(InterruptedException e) {
				e.printStackTrace();
			}
			return Thread.currentThread().getName();
		};

		CompletableFuture<String> cf = supplyAsync(delayedSupplier, executor);
		String result = cf.join();

		System.out.println("Running " + result);

		cf.complete("Tool long"); // doesn't change the value if CompletableFuture is completed.

		String result2 = cf.join();

		System.out.println("Running " + result2);

		executor.shutdown();
	}

	@Test
	public void completeBeforeCompletionExample(){
		ExecutorService executor = Executors.newCachedThreadPool();
		Supplier<String> delayedSupplier = () -> {
			try {
				Thread.sleep(500);
			} catch(InterruptedException e) {
				e.printStackTrace();
			}

			return Thread.currentThread().getName();
		};

		CompletableFuture<String> cf = supplyAsync(delayedSupplier, executor);

		cf.complete("Tool long");

		String result = cf.join();

		assertEquals("Tool long", result);

		String result2 = cf.join();

		assertEquals("Tool long", result2);

		executor.shutdown();
	}

	@Test
	public void obtrudeValueExample(){
		ExecutorService executor = Executors.newCachedThreadPool();
		Supplier<String> delayedSupplier = () -> {
			try {
				Thread.sleep(500);
			} catch(InterruptedException e) {
				e.printStackTrace();
			}
			return Thread.currentThread().getName();
		};

		CompletableFuture<String> cf = supplyAsync(delayedSupplier, executor);

		String result = cf.join();

		System.out.println("Running " + result);

		cf.obtrudeValue("Tool long");

		String result2 = cf.join();

		assertEquals("Tool long", result2);

		executor.shutdown();
	}

	@Test
	public void Two_to_One_Selecting_Patterns(){
		CompletableFuture<String> cf1 = supplyAsync(blocked(String.class));
		CompletableFuture<String> cf2 = supplyAsync(returnValueLater("bar"));
		CompletableFuture<String> cf3 = supplyAsync(blocked(String.class));
		CompletableFuture<String> cf4 = supplyAsync(returnValue("foo"));

		CompletableFuture<String> upstreams = cf1
				.applyToEither(cf2, Function.identity())
				.applyToEither(cf3, Function.identity())
				.applyToEither(cf4, Function.identity());

		upstreams.thenAccept(System.out::println).join();// print "foo"
	}
	@Test
	public void Two_to_One_Selecting_Patterns2() {
		CompletableFuture<String> cf1 = supplyAsync(blocked(String.class));
		CompletableFuture<String> cf2 = supplyAsync(returnValueLater("bar"));
		CompletableFuture<String> cf3 = supplyAsync(blocked(String.class));
		CompletableFuture<String> cf4 = supplyAsync(returnValue("foo"));
		//the first upstream is always blocked.
		//Creates a new incomplete CompletableFuture
		CompletableFuture<String> blocked = new CompletableFuture<>();
		CompletableFuture<String> upstreams = Stream.of(cf1, cf2, cf3, cf4).reduce(blocked,
				(it, upstream) -> it.applyToEither(upstream, Function.identity()));

		upstreams.thenAccept(System.out::println).join();// print "foo"
	}
	@Test
	public void Two_to_One_Selecting_Patterns3() throws ExecutionException, InterruptedException {
		CompletableFuture<String> cf1 = supplyAsync(blocked(String.class));
		CompletableFuture<String> cf2 = supplyAsync(returnValueLater("bar"));
		CompletableFuture<String> cf3 = supplyAsync(blocked(String.class));
		CompletableFuture<String> cf4 = supplyAsync(returnValue("foo"));
		Object result = null;
		result = CompletableFuture.anyOf(cf1, cf2, cf3, cf4).get();
	    System.out.println(result);
	}

	private <T> Supplier<T> returnValue(T value) {
		return returnValue(() -> value);
	}

	//block forever
	private <T> Supplier<T> blocked(Class<T> type) {
		return returnValue(() -> {
			Thread.currentThread().join();
			return null;
		});
	}

	private <T> Supplier<T> returnValueLater(T value) {
		return returnValue(() -> {
			Thread.sleep(100);
			return value;
		});
	}

	private <T> Supplier<T> returnValue(Callable<T> value) {
		return () -> {
			try {
				return value.call();
			} catch (Exception e) { throw new RuntimeException(e); }
		};
	}
	
	private  boolean isUpperCase(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (Character.isLowerCase(s.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private  String delayedUpperCase(String s) {
		randomSleep();
		return s.toUpperCase();
	}

	private  String delayedLowerCase(String s) {
		randomSleep();
		return s.toLowerCase();
	}

	private  void randomSleep() {
		try {
			Thread.sleep(random.nextInt(1000));
		} catch (InterruptedException e) {
			// ...
		}
	}

	private  void sleepEnough() {
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// ...
		}
	}
}
