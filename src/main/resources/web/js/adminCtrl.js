app.controller("adminCtrl", function($scope, $http, $interval) {
    $scope.account = "";
    $scope.showLogin = true;
    $scope.expanded = {};

    var refreshInterval = 2000;
    var pageRefresh = null;

    $scope.startRefresh = function() {
        if (!pageRefresh) {
          pageRefresh = $interval(getAgents, refreshInterval);
        }
    }

    $scope.stopRefresh = function() {
        if (pageRefresh) {
          $interval.cancel(pageRefresh);
          pageRefresh = null;
        }
    }

    $scope.login = function() {
        getAgents(
            function onSuccess(response) {
                $scope.showLogin = false;
                $scope.startRefresh();
            },
            function onError(response) {
                $scope.showLogin = true;
            }
        );
    };

    $scope.createAgent = function() {
        $http({
            method: "POST",
            url: "/agents",
            headers: {
                "jf-auth": $scope.account
            },
            params: {
                name: $scope.newAgentName
            }
        }).then(function onCreate(response){
            $scope.newAgentName = "";
            getAgents();
        });
    }

    $scope.downloadAgent = function(agent) {
        $http({
            method: "GET",
            url: "/agents/" + agent.agentId + "/download",
            responseType: "blob",
            headers: {
                "jf-auth": $scope.account
            }
        }).then(function onSuccess(response){
            var blob = response.data;
            saveAs(blob, "jflop-agent-" + agent.agentName + ".jar");
        });
    }

    $scope.deleteAgent = function(agentId) {
        $http({
            method: "DELETE",
            url: "/agents/" + agentId,
            headers: {
                "jf-auth": $scope.account
            }
        }).then(function onDelete(response){
            getAgents();
        });
    }

    $scope.getConfiguration = function(agent) {
        $http({
            method: "POST",
            url: "/agents/" + agent.agentId + "/command",
            params: {
                feature: "instr-conf",
                command: "get-config"
            },
            headers: {
                "jf-auth": $scope.account
            }
        }).then(function onSuccess(response){
            getAgents();
        });
    }

    $scope.sendConfiguration = function(agent) {
        $http({
            method: "POST",
            url: "/agents/" + agent.agentId + "/command",
            params: {
                feature: "instr-conf",
                command: "set-config",
                data: agent.features['instr-conf'].state.methods
            },
            headers: {
                "jf-auth": $scope.account
            }
        }).then(
            function onSuccess(response) {
                getAgents();
            },
            function onFailure(response) {
                alert(response.data.error + ": " + response.data.message);
                getAgents();
            }
        );
    }

    $scope.takeSnapshot = function(agent) {
        $http({
            method: "POST",
            url: "/agents/" + agent.agentId + "/command",
            params: {
                feature: "snapshot",
                command: "takeSnapshot",
                data: agent.features['snapshot'].state.duration
            },
            headers: {
                "jf-auth": $scope.account
            }
        }).then(
            function onSuccess(response) {
                getAgents();
            },
            function onFailure(response) {
                alert(response.data.error + ": " + response.data.message);
                getAgents();
            }
        );
    }

    function getAgents(successCallback, failureCallback) {
        $http({
            method: "GET",
            url: "/agents",
            headers: {
                "jf-auth": $scope.account
            }
        }).then(
            function onSuccess(response) {
                $scope.agents = response.data;
                var now = new Date().getTime();
                for (var i = 0; i < $scope.agents.length; i++) {
                    var agent = $scope.agents[i];
                    agent.isAlive = agent.lastReportTime && ((now - agent.lastReportTime) < refreshInterval * 3);
                }
                if (typeof(successCallback) === "function") {
                    successCallback(response);
                }
                $scope.startRefresh();
            },
            function onFailure(response) {
                if (typeof(failureCallback) === "function")
                    failureCallback(response)
            }
        );
    }

});