# Release Notes

## 0.3.3

- Update to ssh-transport 0.4.3

## 0.3.2

- Add release function to the Transport protocol
  The allows a transport to cleanup any resources associated with a
  connection. The ssh transport uses this to evict a connection from the
  cache.

- Update to ssh-transport 0.4.2

## 0.3.1

- Reopen connection completely on cache miss
  Jsch needs the connection re-opened from the Session up or it will fail
  with a "JschException: packet corrupt" exception.

- Enable use of temporary ssh-agent
  To avoid temporary keys being added to the system ssh-agent, use a local
  agent when :temp-key is true.

- Update to ssh-transport 0.4.1


## 0.3.0

- Use lein as build tool
  Also deploy to clojars with com.palletops group id.

- Remove reflection warnings

## 0.2.1

- Update to ssh-transport 0.3.2

## 0.2.1

- Update to ssh-transport 0.3.1
  Fixes adding literal keys to ssh-agent.

## 0.2.0

- Require clojure 1.4.0
  Updates to latest ssh and local transports, and drops slingshot usage

- Fix ssh connection caching

- Add logging of cache hits on closed connections

## 0.1.2

- Update to ssh-transport 0.2.2 to prevent trying to add nil keys to the agent

## 0.1.1

- Update to ssh-transport 0.2.1 to enable SSH connect retries


## 0.1.0

- Initial version
