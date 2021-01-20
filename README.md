# Leader election for spring integration with solace

This starter provides [leader election](https://en.wikipedia.org/wiki/Leader_election) based on solace exclusive queues.

## Use cases

### Consumer group wants to schedule tasks

only one member of your [consumer group](https://docs.spring.io/spring-cloud-stream/docs/1.0.0.M4/reference/htmlsingle/index.html#_consumer_group_support) should run the scheduled task.

You have a service/component with the spring standard @Scheduled annotation.
Additional you have the @LeaderAware annotation, that suppress the methode execution if you are not leader of the group "demo". 
```java
@Service
public class MyScheduledService {

    @Scheduled(fixedRateString = "PT3S", initialDelayString = "PT1S")
    @LeaderAware("demo")
    void scheduler() {
        log.info("I am the leader and log it schedules all 3s");
    }
}
```

The `@LeaderAware` annotation can be used on any component methode.
Those methods will return null if the code was not executed.

You have to tell in your application.yaml to auto join the group when your application is ready.
```yaml
spring:
  leader:
    join-groups:
      demo: ON_READINESS
```

### Execute business logic on leader events

An event listener will receive `OnGrantedEvent` and `OnRevokedEvent` events.  
You can get the leader role via `event.getRole()` or use the `condition = "#leaderEvent.role == 'demo'"` option of `@EventListener` to receive events only for a single leader group.

This required the "join-groups" configuration to be "ON_READINESS".

```java
    @EventListener(classes = {AbstractLeaderEvent.class}, condition = "#leaderEvent.role == 'demo'")
    void leaderEventListener(AbstractLeaderEvent leaderEvent)  {
        log.warn("LeaderController: " + leaderEvent);
    }
```

### Test for leadership within business logic

You can test in your business logic for if you are the leader an decide what you want to do.
```java
@Autowired
private SolaceLeaderInitiator leaderInitiator;

private void yourMethode() {
    leaderInitiator.getContext("theNameOfTheRoleC").isLeader()
}

```

### Yield the leadership

In case of a cluster of leader services you may want to hand over the leadership to a local developer process. The leadership can not be taken but hand over to the next processed joined. The leader hierarchy is always by the time when those processed joined the leader group.

```java
@GetMapping(value = "/leader/yield/{role}")
public String yieldLeaderShip(@PathVariable("role") final String role) {
    Context context = leaderInitiator.getContext(role);
    if (context.isLeader()) {
        context.yield();
        return context.isLeader() ? "I am leader AGAIN! It seams as i the only group member" : "I am not longer the leader";
    }
    return "I was not the leader";
}
```

## Config

### join-groups

Below "join-groups" there is a map of leader group names to join methode.

You can either join the application group programmatically like you have to do in the "PROGRAMMATIC" case
or join the leader group using the configuration.
```yaml
spring:
  leader:
    join-groups:
      theNameOfTheRoleA: PROGRAMMATIC
      theNameOfTheRoleB: PROGRAMMATIC
      theNameOfTheRoleC: FIRST_USE
      demo: ON_READINESS
```

### Join group PROGRAMMATIC

This is the DEFAULT.  
You have to join the group like this:

```java
@Autowired
private SolaceLeaderInitiator leaderInitiator;

private void yourMethode() {
    leaderInitiator.joinGroup("theNameOfTheRoleA");
}
```

### Join group FIRST_USE

The leader group will joined at the first time you request the leader context.

```java
@Autowired
private SolaceLeaderInitiator leaderInitiator;

@GetMapping(value = "/leader/status/{role}")
public String isLeader(@PathVariable("role") final String role) {
    return leaderInitiator.getContext(role).isLeader() ? "leader" : "passive";
}
```

### Join group ON_READINESS

The leader group will be joined as soon your application is [ready](https://www.baeldung.com/spring-liveness-readiness-probes).
This allows your to run boostrap code like cache loading via [ApplicationRunner](https://reflectoring.io/spring-boot-execute-on-startup/)
This is useful in combination with the `@LeaderAware` annotation.

## Solace specifics

### Queues

For each leader group a solace queue will be provisioned. The queue name is "leader." + role
It is expected that no messages will pass the queue. Messages will raise an exception. The queue is only needed for the flow activation trigger.

### Timeout

The fail over timeout is managed in the solace ClientProfile. (Broker version >= 9.7)

```
broker# show client smf1 detail 
Client Keepalive:
  Enabled:                    Yes
  Effective Timeout (sec):    10
broker> enable
broker# configure 
broker(configure)# client-profile default message-vpn default 
broker(configure/client-profile)# service 
broker(configure/client-profile/service)# min-keepalive-timeout 10 
broker(configure/client-profile/service)# smf 
broker(configure/client-profile/service/smf)# min-keepalive-enabled 
```

```
broker> show client-profile default message-vpn default detail 
  Client Keepalive 
    Enabled for SMF clients             : Yes 
    Minimum Timeout                     : 10    seconds 
```

