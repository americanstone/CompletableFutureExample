# CompletableFutureExample
my CompletableFuture playground to help me understand the chain and execution order
 
   the execution order conclusions:
   
   the chain of completableFuture execution order can be guaranteed.
   *Async chain and main thread execution order can be guaranteed.
   *Async and non-Async mix chain and main thread execution is kind of random
   which non-Async can be executed in main thread potentially can block the main thread.
   block or not depends on non-Async's previous stage completed or not
   if completed, non-Async task is executed in main thread, otherwise in previous stage thread
   
   always use async
