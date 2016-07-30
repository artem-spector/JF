app.controller("adminCtrl", function($scope, $http, $timeout) {
    $scope.account = "";
    $scope.showLogin = true;
    $scope.expanded = {};

    $scope.login = function() {
        getAgents(
            function onSuccess(response) {
                $scope.showLogin = false;
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
                if (successCallback)
                    successCallback(response);
                $timeout(getAgents, 1000);
            },
            function onFailure(response) {
                if (failureCallback)
                    failureCallback(response)
            }
        );
    }
});