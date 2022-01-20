var __create = Object.create;
var __defProp = Object.defineProperty;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __getProtoOf = Object.getPrototypeOf;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __markAsModule = (target) => __defProp(target, "__esModule", { value: true });
var __export = (target, all) => {
  __markAsModule(target);
  for (var name in all)
    __defProp(target, name, { get: all[name], enumerable: true });
};
var __reExport = (target, module2, desc) => {
  if (module2 && typeof module2 === "object" || typeof module2 === "function") {
    for (let key of __getOwnPropNames(module2))
      if (!__hasOwnProp.call(target, key) && key !== "default")
        __defProp(target, key, { get: () => module2[key], enumerable: !(desc = __getOwnPropDesc(module2, key)) || desc.enumerable });
  }
  return target;
};
var __toModule = (module2) => {
  return __reExport(__markAsModule(__defProp(module2 != null ? __create(__getProtoOf(module2)) : {}, "default", module2 && module2.__esModule && "default" in module2 ? { get: () => module2.default, enumerable: true } : { value: module2, enumerable: true })), module2);
};

// lib/testing/stepfunctions/index.js
__export(exports, {
  handler: () => handler
});
var AWS = __toModule(require("aws-sdk"));
var endpoint = process.env.AWS_ENDPOINT_URL || `http://${process.env.LOCALSTACK_HOSTNAME}:${process.env.EDGE_PORT}`;
var handler = async ({ service, operation, params, region }) => {
  console.log(JSON.stringify(process.env));
  const keys = Reflect.ownKeys(AWS);
  let serviceKey = keys.find((k) => typeof k == "string" && k.toLowerCase() === service.toLowerCase());
  if (serviceKey) {
    try {
      const service2 = Reflect.get(AWS, serviceKey);
      const client = new service2({
        endpoint,
        region,
        credentials: {
          accessKeyId: "test",
          secretAccessKey: "test"
        }
      });
      const fn = Reflect.get(client, operation);
      const response = Reflect.apply(fn, client, [params]);
      const result = await response.promise();
      if (result["ResponseMetadata"]) {
        delete result["ResponseMetadata"];
      }
      return result;
    } catch (e) {
      const newErr = new Error(e.message);
      newErr.name = `${service}.${e.code}`;
      console.error(e);
      throw newErr;
    }
  }
};
// Annotate the CommonJS export names for ESM import in node:
0 && (module.exports = {
  handler
});
