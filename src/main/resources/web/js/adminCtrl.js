app.controller("adminCtrl", function($scope, $http, $interval) {
    $scope.account = "";
    $scope.showLogin = true;
    $scope.expanded = {};

    var refreshInterval = 2000;

    $scope.login = function() {
        getAgents(
            function onSuccess(response) {
                $scope.showLogin = false;
                $interval(getAgents, refreshInterval);
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

    $scope.downloadAgent = function(agentId) {
        $http({
            method: "GET",
            url: "/agents/" + agentId + "/download",
            headers: {
                "jf-auth": $scope.account
            }
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
        }).then(function onSuccess(response){
            getAgents();
        });
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
                if (successCallback) {
                    successCallback(response);
                }
            },
            function onFailure(response) {
                if (failureCallback)
                    failureCallback(response)
            }
        );
    }

});