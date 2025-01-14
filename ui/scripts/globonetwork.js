// BEGIN GLOBONETWORK
var globoNetworkAPI;
globoNetworkAPI = globoNetworkAPI || {};

// capabilities
globoNetworkAPI.capabilities = null;

globoNetworkAPI.getCapability = function(name) {
    if (!globoNetworkAPI.capabilities) {
        $.ajax({
            url: createURL('listGloboNetworkCapabilities'),
            async: false,
            success: function(json) {
                globoNetworkAPI.capabilities = json.listglobonetworkcapabilitiesresponse.globoNetworkCapability;
            }
        });
    }
    return globoNetworkAPI.capabilities[name];
}

globoNetworkAPI.networkDialog = {
    zoneObjs: [],
    physicalNetworkObjs: [],
    networkOfferingObjs: [],
    globoNetworkEnvironmentsObjs: [],
    def: {
        label: 'Add GloboNetwork Network',

        messages: {
            notification: function(args) {
                return 'Add GloboNetwork Network';
            }
        },

        preFilter: function(args) {
            return true; // No restrictions
        },

        createForm: {
            title: 'Add GloboNetwork Network',

            preFilter: function(args) {
                if ('zones' in args.context) {
                    args.$form.find('.form-item[rel=zoneId]').hide();
                } else {
                    args.$form.find('.form-item[rel=zoneId]').css('display', 'inline-block');
                }
                if (!globoNetworkAPI.getCapability('supportCustomNetworkDomain')) {
                    args.$form.find('.form-item[rel=networkdomain]').hide();
                }
            },

            fields: {
                name: {
                    label: 'label.name',
                    validation: {
                        required: true
                    }
                },
                description: {
                    label: 'label.description',
                    validation: {
                        required: true
                    }
                },
                networkdomain: {
                    label: 'label.network.domain',
                    docID: 'helpGuestNetworkZoneNetworkDomain'
                },

                zoneId: {
                    label: 'label.zone',
                    validation: {
                        required: true
                    },
                    select: function(args) {
                        if ('zones' in args.context) {
                            globoNetworkAPI.networkDialog.zoneObjs = args.context.zones;
                        } else {
                            $.ajax({
                                url: createURL('listZones'),
                                async: false,
                                success: function(json) {
                                    globoNetworkAPI.networkDialog.zoneObjs = []; //reset
                                    var items = json.listzonesresponse.zone;
                                    if (items !== null) {
                                        for (var i = 0; i < items.length; i++) {
                                            if (items[i].networktype == 'Advanced') {
                                                globoNetworkAPI.networkDialog.zoneObjs.push(items[i]);
                                            }
                                        }
                                    }
                                }
                            });
                        }
                        args.response.success({
                            data: $.map(globoNetworkAPI.networkDialog.zoneObjs, function(zone) {
                                return {
                                    id: zone.id,
                                    description: zone.name
                                };
                            })
                        });
                    },
                    isHidden: true
                },

                napiEnvironmentId: {
                    label: 'Environment',
                    dependsOn: 'zoneId',
                    select: function(args) {
                        if ('globoNetworkEnvironmentsObjs' in args.context) {
                            globoNetworkAPI.networkDialog.globoNetworkEnvironmentsObjs = args.context.globoNetworkEnvironmentsObjs;
                        } else {
                            var selectedZoneId = args.$form.find('.form-item[rel=zoneId]').find('select').val();
                            $.ajax({
                                url: createURL('listGloboNetworkEnvironments'),
                                data: {
                                    zoneid: selectedZoneId
                                },
                                async: false,
                                success: function(json) {
                                    globoNetworkAPI.networkDialog.globoNetworkEnvironmentsObjs = json.listglobonetworkenvironmentsresponse.globonetworkenvironment;
                                }
                            });
                        }
                        var items = [];
                        if (globoNetworkAPI.networkDialog.globoNetworkEnvironmentsObjs != null) {
                            for (var i = 0; i < globoNetworkAPI.networkDialog.globoNetworkEnvironmentsObjs.length; i++) {
                                items.push({
                                    id: globoNetworkAPI.networkDialog.globoNetworkEnvironmentsObjs[i].napienvironmentid,
                                    description: globoNetworkAPI.networkDialog.globoNetworkEnvironmentsObjs[i].name
                                });
                            }
                        }
                        args.response.success({
                            data: items
                        });
                    },
                    isHidden: false
                },

                scope: {
                    label: 'label.scope',
                    select: function(args) {
                        var selectedZoneId = args.$form.find('.form-item[rel=zoneId]').find('select').val();
                        var selectedZoneObj = {};
                        if (globoNetworkAPI.networkDialog.zoneObjs != null && selectedZoneId != "") {
                            for (var i = 0; i < globoNetworkAPI.networkDialog.zoneObjs.length; i++) {
                                if (globoNetworkAPI.networkDialog.zoneObjs[i].id == selectedZoneId) {
                                    selectedZoneObj = globoNetworkAPI.networkDialog.zoneObjs[i];
                                    break;
                                }
                            }
                        }

                        var array1 = [];
                        if (selectedZoneObj.networktype == "Advanced" && selectedZoneObj.securitygroupsenabled == true) {
                            array1.push({
                                id: 'zone-wide',
                                description: 'All'
                            });
                        } else {
                            if (isAdmin()) {
                                array1.push({
                                    id: 'zone-wide',
                                    description: 'All'
                                });
                            } 
                            if (isAdmin() || isDomainAdmin()) {
                                array1.push({
                                    id: 'domain-specific',
                                    description: 'Domain'
                                });
                                array1.push({
                                    id: 'account-specific',
                                    description: 'Account'
                                });
                                array1.push({
                                    id: 'project-specific',
                                    description: 'Project'
                                });
                            } else if (isUser()) {
                                array1.push({
                                    id: 'my-account',
                                    description: 'My Account'
                                });
                                array1.push({
                                    id: 'project-specific',
                                    description: 'Project'
                                });
                            }
                        }
                        args.response.success({
                            data: array1
                        });

                        args.$select.change(function() {
                            var $form = $(this).closest('form');
                            if ($(this).val() == "zone-wide" || $(this).val() == "my-account") {
                                $form.find('.form-item[rel=domainId]').hide();
                                $form.find('.form-item[rel=subdomainaccess]').hide();
                                $form.find('.form-item[rel=account]').hide();
                                $form.find('.form-item[rel=projectId]').hide();
                            } else if ($(this).val() == "domain-specific") {
                                $form.find('.form-item[rel=domainId]').css('display', 'inline-block');
                                $form.find('.form-item[rel=subdomainaccess]').css('display', 'inline-block');
                                $form.find('.form-item[rel=account]').hide();
                                $form.find('.form-item[rel=projectId]').hide();
                            } else if ($(this).val() == "account-specific") {
                                $form.find('.form-item[rel=domainId]').css('display', 'inline-block');
                                $form.find('.form-item[rel=subdomainaccess]').hide();
                                $form.find('.form-item[rel=account]').css('display', 'inline-block');
                                $form.find('.form-item[rel=projectId]').hide();
                            } else if ($(this).val() == "project-specific") {
                                if (isAdmin() || isDomainAdmin()) {                                
                                    $form.find('.form-item[rel=domainId]').css('display', 'inline-block');
                                    $form.find('.form-item[rel=subdomainaccess]').hide();
                                    $form.find('.form-item[rel=account]').hide();
                                }
                                $form.find('.form-item[rel=projectId]').css('display', 'inline-block');
                            }
                        });
                    }
                },
                domainId: {
                    label: 'label.domain',
                    validation: {
                        required: true
                    },
                    select: function(args) {
                        var items = [];
                        var selectedZoneId = args.$form.find('.form-item[rel=zoneId]').find('select').val();
                        var selectedZoneObj = {};
                        if (globoNetworkAPI.networkDialog.zoneObjs != null && selectedZoneId != "") {
                            for (var i = 0; i < globoNetworkAPI.networkDialog.zoneObjs.length; i++) {
                                if (globoNetworkAPI.networkDialog.zoneObjs[i].id == selectedZoneId) {
                                    selectedZoneObj = globoNetworkAPI.networkDialog.zoneObjs[i];
                                    break;
                                }
                            }
                        }
                        if (isUser()) {
                            // If it is a regular user, send his own domainID
                            items.push({
                                id: args.context.users[0].domainid,
                            });
                        } else if (selectedZoneObj.domainid != null) { //list only domains under selectedZoneObj.domainid
                            $.ajax({
                                url: createURL("listDomainChildren&id=" + selectedZoneObj.domainid + "&isrecursive=true"),
                                dataType: "json",
                                async: false,
                                success: function(json) {
                                    var domainObjs = json.listdomainchildrenresponse.domain;
                                    $(domainObjs).each(function() {
                                        items.push({
                                            id: this.id,
                                            description: this.path
                                        });
                                    });
                                }
                            });
                            $.ajax({
                                url: createURL("listDomains&id=" + selectedZoneObj.domainid),
                                dataType: "json",
                                async: false,
                                success: function(json) {
                                    var domainObjs = json.listdomainsresponse.domain;
                                    $(domainObjs).each(function() {
                                        items.push({
                                            id: this.id,
                                            description: this.path
                                        });
                                    });
                                }
                            });
                        } else { //list all domains
                            $.ajax({
                                url: createURL("listDomains&listAll=true"),
                                dataType: "json",
                                async: false,
                                success: function(json) {
                                    var domainObjs = json.listdomainsresponse.domain;
                                    $(domainObjs).each(function() {
                                        items.push({
                                            id: this.id,
                                            description: this.path
                                        });
                                    });
                                }
                            });
                        }
                        args.response.success({
                            data: items
                        });
                    }
                },
                subdomainaccess: {
                    label: 'label.subdomain.access',
                    isBoolean: true,
                    isHidden: true,
                },
                account: {
                    label: 'label.account'
                },

                projectId: {
                    label: 'label.project',
                    validation: {
                        required: true
                    },
                    select: function(args) {
                        var items = [];
                        $.ajax({
                            url: createURL("listProjects&listAll=true"),
                            dataType: "json",
                            async: false,
                            success: function(json) {
                                projectObjs = json.listprojectsresponse.project;
                                $(projectObjs).each(function() {
                                    items.push({
                                        id: this.id,
                                        description: this.name
                                    });
                                });
                            }
                        });
                        args.response.success({
                            data: items
                        });
                    }
                },

                networkOfferingId: {
                    label: 'label.network.offering',
                    dependsOn: ['zoneId', 'scope'],
                    select: function(args) {
                        var data = {
                            state: 'Enabled',
                            zoneid: args.$form.find('.form-item[rel=zoneId]').find('select').val()
                        };

                        //Network tab in Guest Traffic Type in Infrastructure menu is only available when it's under Advanced zone.
                        //zone dropdown in add guest network dialog includes only Advanced zones.
                        if (args.scope == "zone-wide" || args.scope == "domain-specific") {
                            $.extend(data, {
                                guestiptype: 'Shared'
                            });
                        }

                        var items = [];
                        $.ajax({
                            url: createURL('listNetworkOfferings'),
                            data: data,
                            async: false,
                            success: function(json) {
                                globoNetworkAPI.networkDialog.networkOfferingObjs = json.listnetworkofferingsresponse.networkoffering;
                                if (globoNetworkAPI.networkDialog.networkOfferingObjs != null && globoNetworkAPI.networkDialog.networkOfferingObjs.length > 0) {
                                    var selectedZoneId = args.$form.find('.form-item[rel=zoneId]').find('select').val();
                                    var selectedZoneObj = {};
                                    if (globoNetworkAPI.networkDialog.zoneObjs != null && selectedZoneId != "") {
                                        for (var i = 0; i < globoNetworkAPI.networkDialog.zoneObjs.length; i++) {
                                            if (globoNetworkAPI.networkDialog.zoneObjs[i].id == selectedZoneId) {
                                                selectedZoneObj = globoNetworkAPI.networkDialog.zoneObjs[i];
                                                break;
                                            }
                                        }
                                    }
                                    for (var i = 0; i < globoNetworkAPI.networkDialog.networkOfferingObjs.length; i++) {
                                        //for zone-wide network in Advanced SG-enabled zone, list only SG network offerings
                                        if (selectedZoneObj.networktype == 'Advanced' && selectedZoneObj.securitygroupsenabled == true) {
                                            if (args.scope == "zone-wide") {
                                                var includingSecurityGroup = false;
                                                var serviceObjArray = globoNetworkAPI.networkDialog.networkOfferingObjs[i].service;
                                                for (var k = 0; k < serviceObjArray.length; k++) {
                                                    if (serviceObjArray[k].name == "SecurityGroup") {
                                                        includingSecurityGroup = true;
                                                        break;
                                                    }
                                                }
                                                if (includingSecurityGroup == false)
                                                    continue; //skip to next network offering
                                            }
                                        }
                                        items.push({
                                            id: globoNetworkAPI.networkDialog.networkOfferingObjs[i].id,
                                            description: globoNetworkAPI.networkDialog.networkOfferingObjs[i].displaytext
                                        });
                                    }
                                }
                            }
                        });
                        args.response.success({
                            data: items
                        });
                    }
                }
            }
        }, 

        action: function(args) { //Add GloboNetwork network in advanced zone
            var $form = args.$form;

            var array1 = [];
            array1.push("&zoneId=" + args.data.zoneId);
            array1.push("&networkOfferingId=" + args.data.networkOfferingId);

            //Pass physical network ID to addNetworkViaGloboNetworkCmd API only when network offering's guestiptype is Shared.
            var selectedNetworkOfferingObj;
            if (globoNetworkAPI.networkDialog.networkOfferingObjs != null) {
                for (var i = 0; i < globoNetworkAPI.networkDialog.networkOfferingObjs.length; i++) {
                    if (globoNetworkAPI.networkDialog.networkOfferingObjs[i].id == args.data.networkOfferingId) {
                        selectedNetworkOfferingObj = globoNetworkAPI.networkDialog.networkOfferingObjs[i]
                        break;
                    }
                }
            }

            if (selectedNetworkOfferingObj.guestiptype == "Shared")
                array1.push("&napienvironmentid=" + args.data.napiEnvironmentId);


            array1.push("&name=" + todb(args.data.name));
            array1.push("&displayText=" + todb(args.data.description));
            
            if (globoNetworkAPI.getCapability('supportCustomNetworkDomain')) {
                array1.push("&networkdomain=" + todb(args.data.networkdomain));
            }

            if ($form.find('.form-item[rel=domainId]').css("display") != "none") {
                array1.push("&domainId=" + args.data.domainId);

                if ($form.find('.form-item[rel=account]').css("display") != "none") { //account-specific
                    array1.push("&account=" + args.data.account);
                    array1.push("&acltype=account");
                } else if ($form.find('.form-item[rel=projectId]').css("display") != "none") { //project-specific
                    array1.push("&projectid=" + args.data.projectId);
                    array1.push("&acltype=account");
                } else { //domain-specific
                    array1.push("&acltype=domain");

                    if ($form.find('.form-item[rel=subdomainaccess]:visible input:checked').size())
                        array1.push("&subdomainaccess=true");
                    else
                        array1.push("&subdomainaccess=false");
                }
            } else { //zone-wide
                if (isAdmin() || isDomainAdmin()) {
                    array1.push("&acltype=domain"); //server-side will make it Root domain (i.e. domainid=1)
                } else if (isUser()) {
                    array1.push("&acltype=account");

                    if ($form.find('.form-item[rel=projectId]').css("display") != "none") { //project-specific for user
                        array1.push("&projectid=" + args.data.projectId);
                    } else { // account-specific for user
                        array1.push("&domainId=" + args.data.domainId); // user's domain
                        array1.push("&account=" + args.context.users[0].account); // current user's account
                    }
                }
            }

            $.ajax({
                url: createURL("addNetworkViaGloboNetworkCmd" + array1.join(""), {
                    ignoreProject: true
                }),
                dataType: "json",
                success: function(json) {
                    var item = json.addnetworkviaglobonetworkresponse.network;
                    args.response.success({
                        data: item
                    });
                },
                error: function(XMLHttpResponse) {
                    var errorMsg = parseXMLHttpResponse(XMLHttpResponse);
                    args.response.error(errorMsg);
                }
            });
        },
        notification: {
            poll: function(args) {
                args.complete();
            }
        }
    }
}
// END GLOBONETWORK
