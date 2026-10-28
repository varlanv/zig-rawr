const std = @import("std");
const Translator = @import("translate_c").Translator;

pub fn build(b: *std.Build) !void {
    const target = b.standardTargetOptions(.{});
    const optimize = b.standardOptimizeOption(.{});
    const strip = b.option(
        bool,
        "strip",
        "Remove debug information",
    ) orelse false;

    const exe = b.addExecutable(.{
        .name = "zigrawrAst",
        .root_module = b.createModule(.{
            .strip = strip,
            .root_source_file = b.path("src/main.zig"),
            .target = target,
            .optimize = optimize,
        }),
    });
    b.installArtifact(exe);

    const lib = b.addLibrary(.{
        .linkage = .dynamic,
        .name = "zigrawrAst",
        .root_module = b.createModule(.{
            .strip = strip,
            .root_source_file = b.path("src/root.zig"),
            .target = target,
            .optimize = optimize,
        }),
    });
    b.installArtifact(lib);

    const runStep = b.step("run", "Run the app");
    const runCmd = b.addRunArtifact(exe);
    runStep.dependOn(&runCmd.step);
    runCmd.step.dependOn(b.getInstallStep());
    runCmd.addPassthruArgs();
    const exeTests = b.addTest(.{
        .root_module = exe.root_module,
    });
    const runExeTests = b.addRunArtifact(exeTests);
    const libTests = b.addTest(.{
        .root_module = lib.root_module,
    });
    const runLibTests = b.addRunArtifact(libTests);
    const testStep = b.step("test", "Run tests");
    testStep.dependOn(&runExeTests.step);
    testStep.dependOn(&runLibTests.step);
}
