(ns postman.core
  ^{:doc "postman-gw - a http service to accept requests to send payments."
    :author "SE Ngetha / ngetha@gmail.com"}
  (:gen-class :main true)
  (:use [compojure.route :only [files not-found]]
        [compojure.handler :only [site]] ; form, query params decode; cookie; session, etc
        [compojure.core :refer :all]
        org.httpkit.server)
  (:require [clojure.tools.logging :as log]
            [clojure.data.json :as json]))

(defmacro with-timed-try
  "marco to execute blocks that need to be timed and may throw exceptions"
  [tag & body]
  `(let
     [fn-name# (str "do" (.toUpperCase (subs ~tag 0 1)) (subs ~tag 1))
      start-time# (System/currentTimeMillis)
      e-handler# (fn [err# fname#] (log/error err# (format "!%s" fname#)) :fail)
      return-val# (try
                    ~@body
                    (catch Exception se#
                      (e-handler# se# fn-name#)))]
     (log/infof "%s -> %s" fn-name# return-val#)
     (log/infof "callProf|%s -> %sms" fn-name# (- (System/currentTimeMillis) start-time#))
     return-val#))

(defn read-payment-status
  "fn to check the status of a given transaction (tid)
  should return {:status (1 for OK, 0 for failure), :status-msg (any response msg),
  :ext-data - any additional data you want sent}"
  [tid]
  {:status "1" :status-msg "Sent Successfully"
   :ext-data {:ext-trans-id 474883822 :ext-mpesa-code "XFDG3674"}})

(defn queue-payment
  "fn to queue a payment requestreq is a map with the params (:to, :amt, :via, :tid)
  should return either :q-ok or :q-fail"
  [req]
  :q-ok)

(defn fn-mk-response-map
  "fn shortcut to make http-kit response body"
  [status status-msg]
  {:status status :content-type "application/json"
   :body {:status status :status-msg status-msg}})

(defn fn-mk-response
  "fn that given a http-kit response body transforms body to a json string"
  [resp]
  (into resp {:body (json/write-str (:body resp))}))

(defn get-status
  "fn to get the status of a payment"
  [req]
  (with-timed-try
    "getStatus"
    (log/infof "getStatus")
    (log/debugf "getStatus -> raw params (%s)" (pr-str (:params req)))
    (let [
          params (:params req)
          tid (:tid params)
          ; validate tid
          has-valid-tid (and (not (nil? tid)) (contains? params :tid) (< (count tid) 65))]
      ; debug
      (log/debugf "getStatus(%s) valid? %s" tid has-valid-tid)
      ; if valid call get status
      (if has-valid-tid
        (fn-mk-response {:status 200 :body {:status 200 :status-msg "OK" :tid tid
                                            :resp (read-payment-status tid)}})
        (fn-mk-response (fn-mk-response-map 400 "tid must be <= 64 and must be present"))))))

(defn do-send!
  "fn to send money to a specific recipient"
  [req]
  (with-timed-try
    "doSend!"
    (log/infof "doSend")
    (log/debugf "doSend -> raw params (%s)" (pr-str (:params req)))
    (let [
          ; what keys do we expect
          expected-keys #{:to :amt :tid :via}
          expected-via #{:m-pesa}
          params (:params req)

          ; any nil params
          nil-params? (every? (fn [p] (not (nil? (p params)))) (keys params))
          msisdn-valid? (not (nil? (re-find #"^\+\d{12}$" (:to params)))) ; to must be mobile no in intl format
          tid-valid? (< (count (:tid params)) 65)           ; tid <= 64
          amt-valid? (not (nil? (re-find #"^\d+$" (:amt params)))) ; amt must be an integer

          ; find missing keys
          missing-keys (clojure.set/difference expected-keys (keys params))
          missing-keys-str (clojure.string/join "," missing-keys)
          keys-valid? (= 0 (count missing-keys))

          ; validate via
          via-valid? (contains? expected-via (keyword (:via params)))

          ; list of vals of the outcome
          valid-list-vals (list nil-params? msisdn-valid? tid-valid? amt-valid? via-valid? keys-valid?)
          valid-list-msg (list "No param can be nil" "to must be in Intl format +254xxx" "|tid| must be <= 64"
                            "amt must be an integer"
                            (format "via must be one of %s" (clojure.string/join expected-via))
                            (format "missing keys %s" missing-keys-str))

          ; make it into a map
          valid-list (apply array-map (interleave '(:params :to :tid :amt :via :keys) valid-list-vals))
          valid-list-errors (apply array-map (interleave '(:params :to :tid :amt :via :keys) valid-list-msg))


          ; q response map
          q-resp-map {:q-ok (fn-mk-response-map 202 "Accepted")
                      :q-fail (fn-mk-response-map 500 "Oops! Internal error")}

          ; is all well?
          has-error? (not (reduce (fn [a b] (and a b)) true (vals valid-list)))
          error-msgs (map (fn [p]
                            (log/debugf "%s %s %s %s " p (get valid-list p) has-error? (get valid-list-errors p))
                            (if (not (get valid-list p))
                              (get valid-list-errors p)
                              (format "%s is ok" p))) (keys valid-list))

          ; make the error reponse
          fn-mk-error-response (fn []
                                 (log/info (pr-str error-msgs))
                                 (fn-mk-response (let [err
                                                       (fn-mk-response-map
                                                         400
                                                         (clojure.string/join "," error-msgs))]
                                                   (log/errorf "!doSend -> %s" (pr-str err))
                                                   err)))]
      ; if the request is bad say why
      (if (not has-error?)
        (do
          ; now queue this request
          (fn-mk-response (get q-resp-map (queue-payment params))))
        (do
          (fn-mk-error-response))))))

(defroutes all-routes
           (GET "/" [] (fn [req] "ehlo"))
           (POST "/postman/send" [] do-send!)
           (GET "/postman/status" [] get-status)
           (not-found "404"))

; http server for graceful shutdown
(defonce http-server (atom nil))

(defn- stop-server []
  (when-not (nil? @http-server)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately
    (log/info "Valar Dohaeris - Stopped Http service")
    (@http-server  :timeout 100)
    (reset! http-server nil)
    :stopped))

(defn- start-server []
  (log/info "Valar Morghulis - Starting Http service")
  (reset! http-server (run-server (site  #'all-routes) {:port 9099}))
  :started)

(defn -restart-server []
  (stop-server)
  (start-server))

(defn -main
  "fn to open the post office"
  [& args]
  (with-timed-try
    "startPostOffice"
    (log/info "Postoffice opening!")
    (-restart-server))
  )
