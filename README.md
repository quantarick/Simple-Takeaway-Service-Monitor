# Read Me First

The description of the solution and implementation for the code challenge.

## Analysis

* Production-ready requires a distributed system design, i.e. support multiple nodes
* Poisson Distribution with an average of 3.25 deliveries per second (lambda), according to https://keisan.casio.com/exec/system/1180573179 to cover the lower cumulative probability over 99%, we need to support to 9 TPS
* The client should get updated shelf information when order is added or removed, and considering it's not a bidirectional communication scenario, so SSE is better than WebSocket for this challenge.
* Order decay formula, considering it might move from the overflow shelf to the target shelf which will double or keep the decay rate:

```properties
	V (1) = ShelfLife-(1 + actualDecayRate) * t where V (1) is the value on shelf-1 before shelf-switch and t is the time on the shelf-1
	V (N) = V (N-1)-(1 + actualDecayRate) * t where V (N) is the value on shelf-N and t is the time on shelf-N
```
   The situation in the code challenge is simpler that there are only two cases of state transfer, i.e. N <= 2:

```properties
	case I. V (1), the Order has been put on target shelf/overflow shelf and it will decay or be delivered.
	case II. V (1)-> V (2), the Order has been put on overflow shelf firstly and then moved to the target shelf, then it will be decay or be delivered.
```
* Order value is linear with time, so we can convert the value decay issue to time expire issue and the latter one has a solution in Redisson.

## Solution and Architecture

* Springboot
* Kafka as Event-Driven Backbone
* Redis to provide persist function
* Redisson to provide distributed lock/synchronizers and Netty task scheduling, i.e. Order Expired Event.
* Kafka Reactor + WebFlux SSE to streaming the order update event


## Prerequisite
In order to follow the workshop, it's good idea to have the following prerequisites ready on your system

* JDK 8 or above
* Maven
* IDE supporting Spring development, recommend IDEA
* Redis
* Kafka

### Installing Redis
* https://redis.io/topics/quickstart

As explained in Redis quick start installation section, Redis can be built from source code and installed.

```sh
  wget http://download.redis.io/redis-stable.tar.gz
  tar xvzf redis-stable.tar.gz
  cd redis-stable
  make
```

### Installing Kafka
* Download latest kafka release as explained in https://kafka.apache.org/quickstart

```sh
> tar -xzf kafka_2.12-2.4.1.tgz
> cd kafka_2.12-2.4.1
> bin/zookeeper-server-start.sh config/zookeeper.properties
> bin/kafka-server-start.sh config/server.properties
```

### Build
* under project root, execute

```sh
	mvn clean install -DskipITs
```

### Running from a Command Line
* under the project root, execute below command to start a instance, and by specifying a different port and run again you can start multiple instances.

```sh
	mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

### Running from IDEA
* you can also start multiple instances in IDEA by setting different server ports to *Program arguments*.
```properties
--server.port=8081
```
and *Main class*
```properties
com.engineering.challenge.solution.RedisKafkaSolutionApplication
```

### Running Integration Test
* under project root, execute

```sh
	mvn integration-test
```

## API

### Orders
* create a order, note the delivery service will be automatically called and adding 2~10s delay to pick up the order from shelf.

```properties
POST http://localhost:8080/orders

 {
    "name": "Beef Stew",
    "temp": "hot",
    "shelfLife": 206,
    "decayRate": 0.69
  }


```

### Shelves

```properties
GET http://localhost:8082/shelves/{shelf-type}/stream

e.g. http://localhost:8082/shelves/hot/stream
```
the response of streaming

```js
data:{"type":"hot","orders":[{"name":"Pad See Ew","decayRate":0.72,"temp":"hot","shelfLife":210.0,"normalizedValue":0.5142857142857142},{"name":"Beef Stew","decayRate":0.69,"temp":"hot","shelfLife":206.0,"normalizedValue":0.5242718446601942}]}

data:{"type":"hot","orders":[{"name":"Pad See Ew","decayRate":0.72,"temp":"hot","shelfLife":210.0,"normalizedValue":0.4666666666666667},{"name":"Beef Stew","decayRate":0.69,"temp":"hot","shelfLife":206.0,"normalizedValue":0.47572815533980584}]}

data:{"type":"hot","orders":[{"name":"Pad See Ew","decayRate":0.72,"temp":"hot","shelfLife":210.0,"normalizedValue":0.41904761904761906},{"name":"Beef Stew","decayRate":0.69,"temp":"hot","shelfLife":206.0,"normalizedValue":0.42718446601941745}]}

data:{"type":"hot","orders":[{"name":"Pad See Ew","decayRate":0.72,"temp":"hot","shelfLife":210.0,"normalizedValue":0.37142857142857144},{"name":"Beef Stew","decayRate":0.69,"temp":"hot","shelfLife":206.0,"normalizedValue":0.3786407766990291}]}

data:{"type":"hot","orders":[{"name":"Pad See Ew","decayRate":0.72,"temp":"hot","shelfLife":210.0,"normalizedValue":0.3238095238095238}]}

data:{"type":"hot","orders":[{"name":"Pad See Ew","decayRate":0.72,"temp":"hot","shelfLife":210.0,"normalizedValue":0.2761904761904762}]}

data:{"type":"hot","orders":[]}

data:{"type":"hot","orders":[]}

data:{"type":"hot","orders":[]}

data:{"type":"hot","orders":[]}
```

## Feedback

https://github.com/quantarick/code-challenge

email: quantarick@gmail.com
