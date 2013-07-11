#!/bin/sh
APP_DIR="$(dirname $0)/.."
cd $APP_DIR
sed 's|url: http://owner.aeonbits.org|#url: http://owner.aeonbits.org|' _config.yml > _serve.yml
jekyll serve -w --config _serve.yml
rm _serve.yml
