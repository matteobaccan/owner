#!/bin/sh
APP_DIR="$(dirname $0)/.."
cd $APP_DIR
sed -i \~ 's|url: http://owner.aeonbits.org|#url: http://owner.aeonbits.org|' _config.yml 
jekyll -w serve 
mv _config.yml~ _config.yml
