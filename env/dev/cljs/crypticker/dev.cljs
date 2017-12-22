(ns ^:figwheel-no-load crypticker.dev
  (:require
    [crypticker.core :as core]
    [devtools.core :as devtools]))

(devtools/install!)

(enable-console-print!)

(core/init!)
