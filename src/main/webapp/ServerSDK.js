/**
 * Created by User on 12/31/2016.
 */
/*
 *                                   SOFTWARE USE PERMISSION
 *
 *  By downloading and accessing this software and associated documentation files ("Software") you are granted the
 *  unrestricted right to deal in the Software, including, without limitation the right to use, copy, modify, publish,
 *  sublicense and grant such rights to third parties, subject to the following conditions:
 *
 *  The following copyright notice and this permission notice shall be included in all copies, modifications or
 *  substantial portions of this Software: Copyright © 2016 GSM Association.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS," WITHOUT WARRANTY OF ANY KIND, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 *  HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 *  ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE. YOU
 *  AGREE TO INDEMNIFY AND HOLD HARMLESS THE AUTHORS AND COPYRIGHT HOLDERS FROM AND AGAINST ANY SUCH LIABILITY.
 */

var MobileConnectServerSDK = {

    _config: {
        discoveryUrl: "/MobileConnectHandler",
        authorizationUrl: "/mobileconnect/start_authorization",
        errorPageUrl: "/mobileconnect/mobile_connect_error",
        popupWidth: 400,
        popupHeight: 585
    },

    configure: function (discoveryUrl, authorizationUrl, errorPageUrl) {
        this._config.discoveryUrl = discoveryUrl;
        this._config.authorizationUrl = authorizationUrl;
        this._config.errorPageUrl = errorPageUrl;
    },

    /**
     * Start the mobile connect process
     * This may require displaying an operator selection pop up
     * followed by a authorization pop up.
     */
    startDiscovery: function () {

        var me = this;
        $.ajax({
            cache: false,
            dataType: "json",
            url: me._config.discoveryUrl,
            success: function(data){

                var status = data['status'],
                    action = data['action'],
                    parameter1 = data['parameter1'],
                    parameter2 = data['parameter2'];

                if(status == 'success') {
                    if(action == 'operator_selection') {
                        me.selectOperator(me._config, parameter1);
                    } else if(action == 'authorization') {
                        me.startAuthorization();
                    }
                }
                else {
                    me.displayError(me._config, parameter1, parameter2);
                }
            }
        });
    },

    /**
     * This is called to display the operator selection screens.
     */
    selectOperator: function (config, href){
        var width = config.popupWidth,
            height = config.popupHeight,
            position_x=(screen.width/2)-(width/2),
            position_y=(screen.height/2)-(height/2);
        window.open(href, "selectionOperator", "width="+width+",height="+height+",menubar=0,toolbar=0,directories=0,scrollbars=no,resizable=no,left="+position_x+",top="+position_y+"");
    },

    /**
     * This is called to start authorization.
     * The operator selection page may be displayed first if the operator has not been identified.
     */
    startAuthorization: function() {
        var me = this;

        $.ajax({
            cache: false,
            dataType: "json",
            url: me._config.authorizationUrl,
            success: function(data) {
                var status = data['status'],
                    action = data['action'],
                    parameter1 = data['parameter1'],
                    parameter2 = data['parameter2'];

                if(status == "success"){
                    if(action == "authorization") {
                        me.loginOpenIdConnect(me._config, parameter1, parameter2);
                    } else if (action == "discovery") {
                        me.startDiscovery();
                    }
                }
                else {
                    me.displayError(me._config, parameter1, parameter2);
                }
            }
        });
    },

    /**
     * Called to display the authorization popup.
     */
    loginOpenIdConnect: function(config, url, screenMode){
        var width = config.popupWidth,
            height = config.popupHeight,
            position_x=(screen.width/2)-(width/2),
            position_y=(screen.height/2)-(height/2);
        window.open(url, "loginOpenIdConnect", "width="+width+",height="+height+",menubar=0,toolbar=0,directories=0,scrollbars=yes,resizable=yes,left="+position_x+",top="+position_y+"");
    },

    /**
     * Called by a child popup to start authorization.
     */
    parentRequestAuthorization: function() {
        window.opener.MobileConnectServerSDK.startAuthorization();
    },

    /**
     * Called by a child popup to start discovery.
     */
    parentRequestDiscovery: function() {
        window.opener.MobileConnectServerSDK.startDiscovery();
    },

    displayError: function(config, error, error_description) {
        var width = config.popupWidth,
            height = config.popupHeight,
            position_x=(screen.width/2)-(width/2),
            position_y=(screen.height/2)-(height/2),
            url = config.errorPageUrl + "?"+ $.param({"error": error, "error_description": error_description});

        window.open(url, "mobileConnectError", "width="+width+",height="+height+",menubar=0,toolbar=0,directories=0,scrollbars=yes,resizable=yes,left="+position_x+",top="+position_y+"");
    }
}
