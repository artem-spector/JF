# JF Server design

## Features

A feature is an implementationof some JF logic, which presents both on the client (agent) and the server side.
An agent can support diffent features, and ideally new features can be deployed to the server and assigned to agents dynamically. 
This implies ability to upload the agent side of the feature dynamically. 

The agent and server side of a feature communicate with each other via a protocol, when the server side sends a command, 
and the agent responds to that command by sending its progress or/and data.
The commands and their responses for different features are aggregated in a single HTTP request/response.

## Runtime protocol

* The agent's task manager sends HTTP requests to the server runtime endpoint at regular intervals.
Each request contains the output data reported by all the agent features.
* The server runtime controller receives that request and invokes all the features assigned to this agent. 
It passes to each feature the data reported for the that feature by the agent. 
* The feature interprets the data, and returns a new task if necessary
* The tasks from all the features are aggregated in one JSON document and sent back to the agent in the HTTP response
* The agent distributes the data sent by the server between the agent features

![runtime](https://www.websequencediagrams.com/cgi-bin/cdraw?lz=dGl0bGUgQWdlbnQgcnVudGltZSBwcm90b2NvbAoKVGFza01hbmFnZXItPisAIQVGZWF0dXJlOiBnZXRDb21tYW5kT3V0cHV0CgATDC0tPi0AMgs6IGYAMQYAJAcASg9SAHMGQ29udHJvbGxlcjogUE9TVCAvcnQvYWdlbnRJZCB7ADUNc30KACQRLT4rSkYAgUUFOiByZXBvcgCBJAhzQW5kR2V0VGFza3MoADkOKQoKAC0HAIFYCFAAgXYHAIFfCXBvbGwAKA4pAIFlBgAbDy0tPi0Adwl0YXNrAFEKLT4tAIFQEwCCDAcAgRIFAIE7EwCCKxcAKQYAgwwOAIMLDmV4ZWN1dGUAgxYHAIFpCACDJQcpCgo&s=modern-blue)

## Admin protocol

* Admin UI sends requests to the server admin endpoint at regular intervals
* Admin controller receives those requests and gathers relevant data from the account, its agents, and their features by calling getAdminUIJson() on them.
* All the Json objects are aggregated and sent with the response back to admin UI, which renders them on the page

* the administrator may send a command to the agent via a POST request to the admin endpoint
* Admin controller receives that request and dispatches the command to the appropriate agent's feature. 
  The feature updates its state which may cause sending a task to the agent client via runtime protocol
* The command response contains no data, its effect becomes visible via the polling of /agents

![admin](https://www.websequencediagrams.com/cgi-bin/cdraw?lz=dGl0bGUgQWRtaW4gcHJvdG9jb2wKCgALBVVJLT4rABUFQ29udHJvbGxlcjogR0VUIC9hZ2VudHMAIQcAFAotPitBY2NvdW50OiBnZXQAPwdKc29uCgARBy0tPi0AQhFhAC4GACMFAD8UZ2UAPRRnZQA3GGdlAC4fUACBYgdGZWF0dXJlAEYWABYPAIElFWYAOwYAgSQWAIFjCFVJAIENB1N0YXRlAIIuCACCSBZQT1MAglcISWQvY29tbWFuZCB7AGEHLCAACwcsIGRhdGF9AIE3KWEAg0AGAEkFKAA3DSkAgUMqb2sAgUYdMjAwCg&s=modern-blue)

## Persistency

The persistency layer maintains the state of the agents to support the administration and analysis flows.
Elasticsearch is used as the DB, so we'll use indicies, doc types, and documents to describe the persistent data structure.

### Administrative data

Administrative data contains the accounts, agents created for those accounts, and the features assigned to those agents.
All the account-related data is stored in a single document, however in future other account-related data (like billing) may be stored in separate documents.

~~~~~~~~~~~~~~
  /jf-accounts/account/
                  + accountId
                  + accountName
                  + agents[]
                    + agentId: string, not indexed
                    + agentName: string, not indexed
                    + lastActivity: date
                    + features[]: string, not indexed
                  
~~~~~~~~~~~~~~
  
**Note**
: Account data reflects the latest known state and is not time-bound
  
### Runtime feature data

Runtime feature data contains the states of different features based on the information passed between the agent and server

## Data processing

