# Required through RUBYOPT by `sf-rb coverage`; the bundle process loads it
# before Bundler exists, so only the exec'd rspec starts SimpleCov.
return unless defined?(Bundler)

require "simplecov"

SimpleCov.start do
  coverage_dir "tmp/coverage"
  enable_coverage :line, :method
  formats :json
  skip "/spec/"
end
