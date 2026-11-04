# Required through RUBYOPT by `sf-rb coverage`. The `bundle` process loads it
# before Bundler exists and skips it; rspec, exec'd with bundler/setup first,
# starts SimpleCov from the project's bundle.
return unless defined?(Bundler)

require "simplecov"

SimpleCov.start do
  coverage_dir "tmp/coverage"
  enable_coverage :line, :method
  formats :json
  skip "/spec/"
end
