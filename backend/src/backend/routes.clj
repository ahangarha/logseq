(ns backend.routes
  (:require [reitit.swagger :as swagger]
            [clj-social.core :as social]
            [backend.config :as config]
            [backend.util :as util]
            [backend.auth :as auth]
            [backend.db.user :as u]
            [backend.db.token :as token]
            [backend.db.repo :as repo]
            [ring.util.response :as resp]
            [backend.views.home :as home]
            [backend.interceptors :as interceptors]))

;; TODO: spec validate, authorization (owner?)

(def routes
  [["/swagger.json"
    {:get {:no-doc true
           :swagger {:info {:title "gitnotes api"
                            :description "with pedestal & reitit-http"}}
           :handler (swagger/create-swagger-handler)}}]

   [
    "/"
    {:get {:no-doc true
           :handler (fn [_req]
                      {:status 200
                       :body (home/home)})}}]

   ["/login"
    {:swagger {:tags ["Login"]}}

    ["/github"
     {:get {:summary "Login with github"
            :handler
            (fn [req]
              (let [{:keys [app-key app-secret redirect-uri]} (get-in config/config [:oauth :github])
                    social (social/make-social :github app-key app-secret
                                               (str redirect-uri
                                                    "?referer="
                                                    (get-in req [:headers "referer"] ""))
                                               :state (str (util/uuid))
                                               :scope "user:email,repo")
                    url (social/getAuthorizationUrl social)]
                (resp/redirect url))
              )}}]]
   ["/auth"
    {:swagger {:tags ["Authenticate"]}}

    ["/github"
     {:get {:summary "Authenticate with github"
            :handler
            (fn [{:keys [params] :as req}]
              (if (and (:code params)
                       (:state params))
                (if-let [user (auth/github params)]
                  (-> (resp/redirect config/website-uri)
                      (assoc :cookies (u/generate-tokens (:id user))))
                  {:status 500
                   :body "Internal Error"})
                {:status 401
                 :body "Invalid request"}))}}]]
   ["/api/v1" {:interceptors [interceptors/cookie-interceptor]}
    ["/me"
     {:get {:summary "Get current user's information"
            :handler
            (fn [{:keys [app-context] :as req}]
              (if-let [user (:user app-context)]
                (let [user-id (:id user)]
                  {:status 200
                   :body {:user user
                          :tokens (token/get-user-tokens user-id)
                          :repos (repo/get-user-repos user-id)}})
                {:status 404
                 :body "not-found"}))}}]

    ["/repos"
     {:post {:summary "Add a repo"
             :handler
             (fn [{:keys [app-context body-params] :as req}]
               (let [user (:user app-context)
                     result (repo/insert {:user_id (:id user)
                                          :url (:url body-params)})]
                 {:status 201
                  :body result}))}
      }]

    ["/repos/:id"
     {:patch {:summary "Update a repo's url"
              :handler
              (fn [{:keys [app-context params body-params] :as req}]
                (let [user (:user app-context)
                      result (repo/update (:id params)
                                          (:url body-params))]
                  {:status 200
                   :body result}))}
      :delete {:summary "Delete a repo"
               :handler
               (fn [{:keys [app-context params] :as req}]
                 (let [user (:user app-context)
                       result (repo/delete (:id params))]
                   {:status 200
                    :body {:result true}}))}}]]])
