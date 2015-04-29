HBaseRpc.java
* base class for all rpc requests going to hbase
* implementation of class are not synchronized

PutRequest.java


HBaseClient.java
* constructor creates the session and opens channels
* note that PutRequest <- BatchableRpc <- HBaseRpc
* sendRpcToRegion(HBaseRpc request)
    - handles the actual write
    - the put() methods are all delegated to sendRpcToRegion


HBaseDeferredClient.java
* loading up a pool of threads for asyncall and waiting can be done with async.Deferred and async.Callback
